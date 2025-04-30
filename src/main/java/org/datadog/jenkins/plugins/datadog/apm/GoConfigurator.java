package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;

public class GoConfigurator implements TracerConfigurator {
    private static final Logger LOGGER = Logger.getLogger(GoConfigurator.class.getName());

    private static final String TRACER_VERSION_ENV_VAR = "DD_SET_TRACER_VERSION_GO";
    private static final String LATEST_TAG = "latest";
    private static final String ORCHESTRION_LATEST_URL = "https://api.github.com/repos/datadog/orchestrion/releases/latest";
    private static final String ORCHESTRION_CONTENT_URL = "https://raw.githubusercontent.com/DataDog/orchestrion/";
    private static final String ORCHESTRION_REPO_URL = "github.com/DataDog/orchestrion";
    private static final int SHELL_CMD_TIMEOUT_MILLIS = 300_000;

    private static final Pattern GO_VERSION_PATTERN = Pattern.compile("^.*?go((\\d+\\.?)+).*?$"); // e.g. "go version go1.23.6 linux/arm64"
    private static final Pattern GO_MOD_VERSION_PATTERN = Pattern.compile("go ((\\d+\\.?)+)"); // e.g. "go 1.23.2"
    private static final Pattern ORCHESTRION_TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"(?<tag>[^\"]+)\"");
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{7,40}");

    private static final Semver MIN_SUPPORTED_VERSION = Semver.parse("1.1");

    private static final int HTTP_TIMEOUT_MILLIS = 60_000;
    private final HttpClient httpClient = new HttpClient(HTTP_TIMEOUT_MILLIS);

    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    @Override
    public Map<String, String> configure(TestOptimization testOptimization, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        // Check if go is installed
        String goVersionOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_CMD_TIMEOUT_MILLIS, "go", "version"));
        Matcher goVersionMatcher = GO_VERSION_PATTERN.matcher(goVersionOutput);
        if (!goVersionMatcher.find()) {
            listener.getLogger().println("[datadog] Invalid go version installed. Skipping tracer installation");
            return Collections.emptyMap();
        }
        Semver installedVersion = Semver.parse(goVersionMatcher.group(1));
        if (installedVersion.compareTo(MIN_SUPPORTED_VERSION) < 0) {
            listener.getLogger().println("[datadog] Minimum supported Go version is " + MIN_SUPPORTED_VERSION + ", current version is " + installedVersion + ". Will skip tracer installation");
            return Collections.emptyMap();
        }

        if (!workspacePath.child("go.mod").exists()) {
            // the workspace is not a golang project?
            return Collections.emptyMap();
        }

        listener.getLogger().println("[datadog] Configuring DD Go tracer: got go version " + goVersionOutput + " from " + workspacePath + " on " + node);

        // Get the tracer version from environment variable or use "latest" as default
        String tracerVersion = getEnvVariable(testOptimization, TRACER_VERSION_ENV_VAR, LATEST_TAG);

        // Get the required Go version from orchestrion's go.mod file
        String orchestrionGoVersion = getOrchestrionGoVersion(workspacePath, tracerVersion, listener);

        // Compare the installed version with the required version
        Semver requiredVersion = Semver.parse(orchestrionGoVersion);

        if (installedVersion.compareTo(requiredVersion) < 0) {
            throw new IllegalStateException("Go version " + installedVersion + " is less than minimum required version " + requiredVersion);
        }

        // Install orchestrion using go install
        String installOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(),
                SHELL_CMD_TIMEOUT_MILLIS,
                "go", "install", ORCHESTRION_REPO_URL + "@" + tracerVersion));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: installed orchestrion. " + installOutput);

        // Pin orchestrion
        String pinOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(),
                SHELL_CMD_TIMEOUT_MILLIS,
                "orchestrion", "pin"));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: pin orchestrion. " + pinOutput);

        // Run go get to update dependencies
        String getOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(),
                SHELL_CMD_TIMEOUT_MILLIS,
                "go", "get", ORCHESTRION_REPO_URL));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: updated dependencies. " + getOutput);

        // Get orchestrion version
        String orchestrionVersion = "";
        try {
            String orchestrionVersionOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(),
                    SHELL_CMD_TIMEOUT_MILLIS,
                    "orchestrion", "version"));
            orchestrionVersion = orchestrionVersionOutput.split(" ")[1]; // Format: "orchestrion v1.0.2"
        } catch (Exception e) {
            orchestrionVersion = "vlatest";
        }
        listener.getLogger().println("[datadog] Configured DD Go tracer with orchestrion version: " + orchestrionVersion);

        // Set up environment variables
        Map<String, String> variables = new HashMap<>();
        String goFlag = "'-toolexec=orchestrion toolexec'";
        variables.put("GOFLAGS", PropertyUtils.append(envs, "GOFLAGS", goFlag));
        variables.put("DD_TRACER_VERSION_GO", orchestrionVersion);

        return variables;
    }

    private String getOrchestrionGoVersion(FilePath workspacePath, String tracerVersion, TaskListener listener) throws Exception {
        String tag = "";

        // If "latest" is provided, fetch the latest release tag from GitHub API
        if (tracerVersion.equals(LATEST_TAG)) {
            String latestReleaseJson = httpClient.get(ORCHESTRION_LATEST_URL, Collections.emptyMap(), response -> response);
            // Extract the tag_name from the JSON response
            Matcher tagMatcher = ORCHESTRION_TAG_PATTERN.matcher(latestReleaseJson);
            if (tagMatcher.find()) {
                tag = tagMatcher.group("tag");
            }

            if (tag.isEmpty()) {
                throw new IllegalStateException("Could not retrieve the latest tag for orchestrion");
            }
        } else {
            tag = tracerVersion;
        }

        // Determine the URL to fetch the go.mod file
        String url;
        Matcher shaMatcher = SHA_PATTERN.matcher(tag);
        if (shaMatcher.matches()) {
            // Tag looks like a commit SHA
            url = ORCHESTRION_CONTENT_URL + tag + "/go.mod";
        } else {
            url = ORCHESTRION_CONTENT_URL + "refs/tags/" + tag + "/go.mod";
        }

        // Fetch the go.mod file content
        String goMod;
        goMod = httpClient.get(url, Collections.emptyMap(), response -> response);

        // Extract the Go version by searching for the line starting with "go "
        for (String line : goMod.split("\n")) {
            Matcher versionMatcher = GO_MOD_VERSION_PATTERN.matcher(goMod);
            if (versionMatcher.find()) {
                return versionMatcher.group(1);
            }
        }

        listener.getLogger().println("[datadog] Could not extract the Go version from go.mod");
        return null;
    }

    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    @Override
    public boolean isConfigurationValid(Node node, FilePath workspacePath) {
        try {
            return workspacePath.child("go.mod").exists() &&
                    workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_CMD_TIMEOUT_MILLIS, "go", "mod", "graph")).contains("orchestrion");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String getEnvVariable(TestOptimization testOptimization, String name, String defaultValue) {
        Map<String, String> additionalVariables = testOptimization.getAdditionalVariables();
        if (additionalVariables != null) {
            String envVariable = additionalVariables.get(name);
            if (envVariable != null) {
                return envVariable;
            }
        }
        String systemValue = System.getenv(name);
        if (systemValue != null) {
            return systemValue;
        }
        return defaultValue;
    }
}
