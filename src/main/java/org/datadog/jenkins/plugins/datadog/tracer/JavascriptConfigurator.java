package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.FilePath;
import hudson.model.Node;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class JavascriptConfigurator implements TracerConfigurator {

    private static final Logger LOGGER = Logger.getLogger(JavascriptConfigurator.class.getName());

    private static final int GET_NPM_VERSION_TIMEOUT_MILLIS = 30_000;
    private static final int INSTALL_TRACER_TIMEOUT_MILLIS = 300_000;

    @Override
    public Map<String, String> configure(DatadogTracerJobProperty<?> tracerConfig, Node node, FilePath workspacePath, Map<String, String> envs) throws Exception {
        String nodeVersion = workspacePath.act(new ShellCommandCallable(GET_NPM_VERSION_TIMEOUT_MILLIS, "npm", "-v"));
        LOGGER.log(Level.FINE, "Got npm version " + nodeVersion + " from " + workspacePath + " on " + node);

        String installTracerOutput = workspacePath.act(new ShellCommandCallable(INSTALL_TRACER_TIMEOUT_MILLIS, "npm", "install", "dd-trace"));
        LOGGER.log(Level.FINE, "Tracer installed in " + workspacePath + " on " + node + "; output: " + installTracerOutput);

        Path absoluteWorkspacePath = Paths.get(workspacePath.absolutize().getRemote());
        Path tracerPath = absoluteWorkspacePath.resolve("node_modules/dd-trace");

        Map<String, String> variables = new HashMap<>();
        variables.put("DD_TRACE_PATH", tracerPath.toString());
        variables.put("NODE_OPTIONS", PropertyUtils.prepend(envs, "NODE_OPTIONS", "-r ./example.js -r $DD_TRACE_PATH/ci/init"));
        return variables;
    }
}
