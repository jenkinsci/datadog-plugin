package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.FilePath;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.MasterToSlaveFileCallable;
import org.datadog.jenkins.plugins.datadog.util.ShellCommandExecutor;

final class JavascriptConfigurator implements TracerConfigurator {

    private static final Logger LOGGER = Logger.getLogger(JavascriptConfigurator.class.getName());

    @Override
    public Map<String, String> configure(DatadogTracerJobProperty<?> tracerConfig, Node node, FilePath workspacePath, Map<String, String> envs) throws Exception {
        try {
            String nodeVersion = workspacePath.act(new GetNpmVersion());
            LOGGER.log(Level.FINE, "Got npm version " + nodeVersion + " from " + workspacePath + " on " + node);

            String installTracerOutput = workspacePath.act(new InstallTracer());
            LOGGER.log(Level.FINE, "Trace installed in " + workspacePath + " on " + node + "; output: " + installTracerOutput);

            Path absoluteWorkspacePath = Paths.get(workspacePath.absolutize().getRemote());
            Path tracerPath = absoluteWorkspacePath.resolve("node_modules/dd-trace");

            Map<String, String> variables = new HashMap<>();
            variables.put("DD_TRACE_PATH", tracerPath.toString());
            variables.put("NODE_OPTIONS", PropertyUtils.prepend(envs, "NODE_OPTIONS", "-r ./example.js -r $DD_TRACE_PATH/ci/init"));
            return variables;

        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Exception while trying to get node version from " + workspacePath + " on " + node + "; assuming node is not installed", e);
            return Collections.emptyMap();
        }
    }

    private static final class GetNpmVersion extends MasterToSlaveFileCallable<String> {
        @Override
        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                ShellCommandExecutor shellCommandExecutor = new ShellCommandExecutor(workspace, 30_000);
                return shellCommandExecutor.executeCommand(new ShellCommandExecutor.ToStringOutputParser(), "npm", "-v");
            } catch (IOException | InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    private static final class InstallTracer extends MasterToSlaveFileCallable<String> {
        @Override
        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                ShellCommandExecutor shellCommandExecutor = new ShellCommandExecutor(workspace, 300_000);
                return shellCommandExecutor.executeCommand(new ShellCommandExecutor.ToStringOutputParser(), "npm", "install", "dd-trace");
            } catch (IOException | InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
