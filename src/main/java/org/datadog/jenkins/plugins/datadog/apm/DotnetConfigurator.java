package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DotnetConfigurator implements TracerConfigurator {
    private static final int GET_DOTNET_VERSION_TIMEOUT_MILLIS = 30_000;
    private static final int INSTALL_TRACER_TIMEOUT_MILLIS = 300_000;
    private static final int SHOW_TRACER_VARS_TIMEOUT_MILLIS = 30_000;

    @Override
    public Map<String, String> configure(TestOptimization testOptimization, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        String dotnetVersion = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_DOTNET_VERSION_TIMEOUT_MILLIS, "dotnet", "--version"));
        listener.getLogger().println("[datadog] Configuring DD .NET tracer: got .NET version " + dotnetVersion + " from " + workspacePath + " on " + node);

        String installTracerOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), INSTALL_TRACER_TIMEOUT_MILLIS, "dotnet", "tool", "update", "--tool-path", workspacePath.getRemote(), "dd-trace"));
        listener.getLogger().println("[datadog] Configuring DD .NET tracer: tracer installed in " + workspacePath + " on " + node + "; output: " + installTracerOutput);

        String tracerVarOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHOW_TRACER_VARS_TIMEOUT_MILLIS, workspacePath.getRemote() + File.separator + "dd-trace", "ci", "configure", "jenkins"));

        Map<String, String> variables = new HashMap<>();
        for (String line : tracerVarOutput.split("\n")) {
            if (!line.contains("=")) {
                continue;
            }
            String[] tokens = line.split("=");
            variables.put(tokens[0], tokens.length == 2 ? tokens[1] : "");
        }
        return variables;
    }
}
