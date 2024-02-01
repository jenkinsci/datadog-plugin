package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class JavascriptConfigurator implements TracerConfigurator {

    private static final int GET_NPM_VERSION_TIMEOUT_MILLIS = 30_000;
    private static final int INSTALL_TRACER_TIMEOUT_MILLIS = 300_000;

    @Override
    public Map<String, String> configure(DatadogTracerJobProperty<?> tracerConfig, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
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
        Path tracerPath = absoluteDatadogPath.resolve("lib/node_modules/dd-trace");
        listener.getLogger().println("[datadog] Configuring DD JS tracer: tracer installed in " + tracerPath + " on " + node + "; output: " + installTracerOutput);

        Map<String, String> variables = new HashMap<>();
        variables.put("DD_TRACE_PATH", tracerPath.toString());
        variables.put("NODE_OPTIONS", PropertyUtils.prepend(envs, "NODE_OPTIONS", "-r $DD_TRACE_PATH/ci/init"));
        return variables;
    }
}
