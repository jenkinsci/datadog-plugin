package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;
import org.datadog.jenkins.plugins.datadog.util.ComparableVersion;

public class GoConfigurator implements TracerConfigurator {

    private static final int SHELL_COMMAND_TIMEOUT_MILLIS = 180_000;

    private static final Pattern GO_VERSION_PATTERN = Pattern.compile("^.*?go((\\d+\\.?)+).*?$"); // e.g. "go version go1.23.6 linux/arm64"

    private static final ComparableVersion MIN_SUPPORTED_VERSION = ComparableVersion.parse("1.11");

    @Override
    public Map<String, String> configure(TestOptimization testOptimization, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        String goVersion = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_COMMAND_TIMEOUT_MILLIS, "go", "version"));
        if (!isVersionSupported(goVersion)){
            listener.getLogger().println("[datadog] Minimum supported Go version is " + MIN_SUPPORTED_VERSION + ", current version is " + goVersion + ". Will skip tracer installation");
            return Collections.emptyMap();
        }

        if (!workspacePath.child("go.mod").exists()) {
            // the workspace is not a golang project?
            return Collections.emptyMap();
        }

        String installTracerOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_COMMAND_TIMEOUT_MILLIS, "go", "install", "github.com/DataDog/orchestrion@latest"));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: tracer installed. " + installTracerOutput);

        String pinOrchestrionOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_COMMAND_TIMEOUT_MILLIS, "orchestrion", "pin"));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: orchestrion pinned. " + pinOrchestrionOutput);

        String updateDependenciesOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_COMMAND_TIMEOUT_MILLIS, "go", "get", "github.com/DataDog/orchestrion"));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: dependencies updated. " + updateDependenciesOutput);

        return Collections.singletonMap("GOFLAGS", PropertyUtils.prepend(envs, "GOFLAGS", "'-toolexec=orchestrion toolexec'"));
    }

    private static boolean isVersionSupported(String goVersionString){
      Matcher matcher = GO_VERSION_PATTERN.matcher(goVersionString);
      if (!matcher.find()) {
        return false;
      }

      ComparableVersion version = ComparableVersion.parse(matcher.group(1));
      return version.compareTo(MIN_SUPPORTED_VERSION) >= 0;
    }

    @Override
    public boolean isConfigurationValid(Node node, FilePath workspacePath) {
      try {
        return workspacePath.child("go.mod").exists() &&
            workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_COMMAND_TIMEOUT_MILLIS, "go", "mod", "graph")).contains("orchestrion");

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;

      } catch (Exception e) {
        return false;
      }
    }
}
