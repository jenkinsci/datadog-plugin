package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;

public class GolangConfigurator implements TracerConfigurator {

    private static final int SHELL_COMMAND_TIMEOUT_MILLIS = 180_000;

    @Override
    public Map<String, String> configure(TestOptimization testOptimization, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        if (!workspacePath.child("go.mod").exists()) {
            // the workspace is not a golang project?
            return Collections.emptyMap();
        }

        String goVersion = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_COMMAND_TIMEOUT_MILLIS, "go", "version"));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: got Go version " + goVersion + " from " + workspacePath + " on " + node);

        String installTracerOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_COMMAND_TIMEOUT_MILLIS, "go", "install", "github.com/DataDog/orchestrion@latest"));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: tracer installed. " + installTracerOutput);

        String pinOrchestrionOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_COMMAND_TIMEOUT_MILLIS, "orchestrion", "pin"));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: orchestrion pinned. " + pinOrchestrionOutput);

        String updateDependenciesOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHELL_COMMAND_TIMEOUT_MILLIS, "go", "get", "github.com/DataDog/orchestrion"));
        listener.getLogger().println("[datadog] Configuring DD Go tracer: dependencies updated. " + updateDependenciesOutput);

        return Collections.singletonMap("GOFLAGS", PropertyUtils.prepend(envs, "GOFLAGS", "'-toolexec=orchestrion toolexec'"));
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
