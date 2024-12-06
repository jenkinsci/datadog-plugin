package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RubyConfigurator implements TracerConfigurator {
    private static final int GET_VERSION_TIMEOUT_MILLIS = 30_000;
    private static final int INSTALL_TRACER_TIMEOUT_MILLIS = 300_000;

    @Override
    public Map<String, String> configure(TestOptimization testOptimization, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        String rubyVersion = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_VERSION_TIMEOUT_MILLIS, "ruby", "-v"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: got ruby version " + rubyVersion + " from " + workspacePath + " on " + node);

        String bundlerVersion = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_VERSION_TIMEOUT_MILLIS, "bundle", "-v"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: got bundler version " + bundlerVersion + " from " + workspacePath + " on " + node);

        String rubygemsVersion = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_VERSION_TIMEOUT_MILLIS, "gem", "-v"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: got rubygems version " + rubygemsVersion + " from " + workspacePath + " on " + node);

        String unfreezeBundleOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), INSTALL_TRACER_TIMEOUT_MILLIS, "bundle", "config", "set", "frozen", "false"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: unfrozen bundle in " + workspacePath + " on " + node + "; output" + unfreezeBundleOutput);

        String installTracerOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), INSTALL_TRACER_TIMEOUT_MILLIS, "bundle", "add", "datadog-ci"));
        listener.getLogger().println("[datadog] Configuring DD Ruby tracer: tracer installed in " + workspacePath + " on " + node + "; output: " + installTracerOutput);

        Map<String, String> variables = new HashMap<>();
        variables.put("RUBYOPT", "-rbundler/setup -rdatadog/ci/auto_instrument");
        return variables;
    }
}
