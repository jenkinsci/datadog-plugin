package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;

final class PythonConfigurator implements TracerConfigurator {

    private static final Logger LOGGER = Logger.getLogger(PythonConfigurator.class.getName());

    private static final int GET_PIP_VERSION_TIMEOUT_MILLIS = 30_000;
    private static final int INSTALL_TRACER_TIMEOUT_MILLIS = 300_000;
    private static final int SHOW_TRACER_PACKAGE_DETAILS_TIMEOUT_MILLIS = 300_000;

    @Override
    public Map<String, String> configure(TestOptimization testOptimization, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        String pipVersion = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_PIP_VERSION_TIMEOUT_MILLIS, "pip", "-V"));
        listener.getLogger().println("[datadog] Configuring DD Python tracer: got pip version " + pipVersion + " from " + workspacePath + " on " + node);

        String installTracerOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), INSTALL_TRACER_TIMEOUT_MILLIS, "pip", "install", "-U", "ddtrace"));
        listener.getLogger().println("[datadog] Configuring DD Python tracer: tracer installed in " + workspacePath + " on " + node + "; output: " + installTracerOutput);

        String tracerLocation = getTracerLocation(workspacePath);

        Map<String, String> variables = new HashMap<>();
        variables.put("PYTEST_ADDOPTS", PropertyUtils.prepend(envs, "PYTEST_ADDOPTS", "--ddtrace"));
        variables.put("PYTHONPATH", PropertyUtils.prepend(envs, "PYTHONPATH", tracerLocation + ":"));
        return variables;
    }

    private static String getTracerLocation(FilePath workspacePath) throws IOException, InterruptedException {
        String getTracerLocationOutput = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), SHOW_TRACER_PACKAGE_DETAILS_TIMEOUT_MILLIS, "pip", "show", "ddtrace"));
        for (String line : getTracerLocationOutput.split("\n")) {
            if (line.contains("Location")) {
                return line.substring(line.indexOf(':') + 2);
            }
        }
        throw new IllegalStateException("Could not determine tracer location in " + workspacePath + "; command output is: " + getTracerLocationOutput);
    }

    @Override
    public boolean isConfigurationValid(Node node, FilePath workspacePath) {
        try {
            String tracerLocation = getTracerLocation(workspacePath);
            return workspacePath.child(tracerLocation).exists();
        } catch (Exception e) {
            DatadogUtilities.logException(LOGGER, Level.FINE, "Could not verify Python tracer file existence", e);
            return false;
        }
    }
}
