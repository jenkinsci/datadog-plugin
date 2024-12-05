package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;

final class JavascriptConfigurator implements TracerConfigurator {

    private static final Logger LOGGER = Logger.getLogger(JavascriptConfigurator.class.getName());

    private static final int GET_NPM_VERSION_TIMEOUT_MILLIS = 30_000;
    private static final int INSTALL_TRACER_TIMEOUT_MILLIS = 300_000;

    private static final String RELATIVE_TRACER_PATH = "lib/node_modules/dd-trace";

    @Override
    public Map<String, String> configure(TestOptimization testOptimization, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        String nodeVersion = workspacePath.act(new ShellCommandCallable(Collections.emptyMap(), GET_NPM_VERSION_TIMEOUT_MILLIS, "npm", "-v"));
        listener.getLogger().println("[datadog] Configuring DD JS tracer: got npm version " + nodeVersion + " from " + workspacePath + " on " + node);

        FilePath datadogPath = workspacePath.child(".datadog");
        datadogPath.mkdirs();

        // set location for installing global packages (Jenkins agent user may not have the permissions to write to the default one)
        Path absoluteDatadogPath = Paths.get(datadogPath.absolutize().getRemote());
        Map<String, String> environment = Collections.singletonMap("NPM_CONFIG_PREFIX", absoluteDatadogPath.toString());

        // we install dd-trace as a "global" package
        // (otherwise, doing SCM checkout might rollback the changes to package.json, and any subsequent `npm install` calls will result in removing the package)
        String installTracerOutput = workspacePath.act(new ShellCommandCallable(environment, INSTALL_TRACER_TIMEOUT_MILLIS, "npm", "install", "-g", "dd-trace"));
        Path tracerPath = absoluteDatadogPath.resolve(RELATIVE_TRACER_PATH);
        listener.getLogger().println("[datadog] Configuring DD JS tracer: tracer installed in " + tracerPath + " on " + node + "; output: " + installTracerOutput);

        Map<String, String> variables = new HashMap<>();
        variables.put("DD_TRACE_PATH", tracerPath.toString());
        variables.put("NODE_OPTIONS", PropertyUtils.prepend(envs, "NODE_OPTIONS", String.format("-r %s/ci/init", tracerPath)));
        return variables;
    }

    @Override
    public boolean isConfigurationValid(Node node, FilePath workspacePath) {
        try {
            FilePath datadogPath = workspacePath.child(".datadog");
            FilePath tracerPath = datadogPath.child(RELATIVE_TRACER_PATH);
            return tracerPath.exists();
        } catch (Exception e) {
            DatadogUtilities.logException(LOGGER, Level.FINE, "Could not verify JS tracer file existence", e);
            return false;
        }
    }
}
