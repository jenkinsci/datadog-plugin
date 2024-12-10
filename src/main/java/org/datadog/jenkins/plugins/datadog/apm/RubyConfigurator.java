package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RubyConfigurator implements TracerConfigurator {
    private static final Semver MIN_RUBY_VERSION = new Semver(2, 7, 0);
    private static final Semver MIN_RUBYGEMS_VERSION = new Semver(3, 3, 22);

    private static final int GET_VERSION_TIMEOUT_MILLIS = 30_000;
    private static final int INSTALL_TRACER_TIMEOUT_MILLIS = 300_000;

    @Override
    public Map<String, String> configure(TestOptimization testOptimization, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        validateRuby(node, workspacePath, listener);
        validateBundler(node, workspacePath, listener);
        validateRubygems(node, workspacePath, listener);

        String unfreezeBundleOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), INSTALL_TRACER_TIMEOUT_MILLIS, "bundle", "config", "set", "frozen", "false"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: unfrozen bundle in " + workspacePath + " on " + node + "; output" + unfreezeBundleOutput);

        String installTracerOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), INSTALL_TRACER_TIMEOUT_MILLIS, "bundle", "add", "datadog-ci"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: tracer installed in " + workspacePath + " on " + node + "; output: " + installTracerOutput);

        Map<String, String> variables = new HashMap<>();
        variables.put("RUBYOPT", "-rbundler/setup -rdatadog/ci/auto_instrument");
        return variables;
    }

    @Override
    public boolean isConfigurationValid(Node node, FilePath workspacePath) {
        try {
            String tracerLocation = getTracerLocation(workspacePath);
            return workspacePath.child(tracerLocation).exists();
        } catch (Exception e) {
            return false;
        }
    }

    private static void validateRubygems(Node node, FilePath workspacePath, TaskListener listener) throws IOException, InterruptedException {
        String rubygemsVersionOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_VERSION_TIMEOUT_MILLIS, "gem", "-v"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: got rubygems version " + rubygemsVersionOutput + " from " + workspacePath + " on " + node);
        Semver rubygemsVersion = Semver.parse(rubygemsVersionOutput);
        if (rubygemsVersion.compareTo(MIN_RUBYGEMS_VERSION) < 0) {
            throw new IllegalStateException("Rubygems version " + rubygemsVersion + " is less than the minimum required version " + MIN_RUBYGEMS_VERSION);
        }
    }

    private static void validateBundler(Node node, FilePath workspacePath, TaskListener listener) throws IOException, InterruptedException {
        String bundlerVersionOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_VERSION_TIMEOUT_MILLIS, "bundle", "-v"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: got bundler version " + bundlerVersionOutput + " from " + workspacePath + " on " + node);
    }

    private static void validateRuby(Node node, FilePath workspacePath, TaskListener listener) throws IOException, InterruptedException {
        String rubyVersionOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_VERSION_TIMEOUT_MILLIS, "ruby", "-v"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: got ruby version " + rubyVersionOutput + " from " + workspacePath + " on " + node);
        Semver rubyVersion = Semver.parse(rubyVersionOutput.split(" ")[1]);
        if (rubyVersion.compareTo(MIN_RUBY_VERSION) < 0) {
            throw new IllegalStateException("Ruby version " + rubyVersion + " is less than the minimum required version " + MIN_RUBY_VERSION);
        }
    }

    private static String getTracerLocation(FilePath workspacePath) throws IOException, InterruptedException {
        return workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_VERSION_TIMEOUT_MILLIS, "bundle", "show", "datadog-ci")).trim();
    }
}
