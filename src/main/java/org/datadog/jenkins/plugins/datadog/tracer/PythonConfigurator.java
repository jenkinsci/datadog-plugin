package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.FilePath;
import hudson.model.Node;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PythonConfigurator implements TracerConfigurator {

    private static final Logger LOGGER = Logger.getLogger(PythonConfigurator.class.getName());

    private static final int GET_PIP_VERSION_TIMEOUT_MILLIS = 30_000;
    private static final int INSTALL_TRACER_TIMEOUT_MILLIS = 300_000;
    private static final int SHOW_TRACER_PACKAGE_DETAILS_TIMEOUT_MILLIS = 300_000;

    @Override
    public Map<String, String> configure(DatadogTracerJobProperty<?> tracerConfig, Node node, FilePath workspacePath, Map<String, String> envs) throws Exception {
        String pipVersion = workspacePath.act(new ShellCommandCallable(GET_PIP_VERSION_TIMEOUT_MILLIS, "pip", "-V"));
        LOGGER.log(Level.FINE, "Got pip version " + pipVersion + " from " + workspacePath + " on " + node);

        String installTracerOutput = workspacePath.act(new ShellCommandCallable(INSTALL_TRACER_TIMEOUT_MILLIS, "pip", "install", "-U", "ddtrace"));
        LOGGER.log(Level.FINE, "Tracer installed in " + workspacePath + " on " + node + "; output: " + installTracerOutput);

        String tracerLocation = getTracerLocation(workspacePath);

        Map<String, String> variables = new HashMap<>();
        variables.put("PYTEST_ADDOPTS", PropertyUtils.prepend(envs, "PYTEST_ADDOPTS", "--ddtrace"));
        variables.put("PYTHONPATH", PropertyUtils.prepend(envs, "PYTHONPATH", tracerLocation + ":"));
        return variables;
    }

    private static String getTracerLocation(FilePath workspacePath) throws IOException, InterruptedException {
        String getTracerLocationOutput = workspacePath.act(new ShellCommandCallable(SHOW_TRACER_PACKAGE_DETAILS_TIMEOUT_MILLIS, "pip", "show", "ddtrace"));
        for (String line : getTracerLocationOutput.split("\n")) {
            if (line.contains("Location")) {
                return line.substring(line.indexOf(':') + 2);
            }
        }
        throw new IllegalStateException("Could not determine tracer location in " + workspacePath + "; command output is: " + getTracerLocationOutput);
    }
}
