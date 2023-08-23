package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.FilePath;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.MasterToSlaveFileCallable;
import org.datadog.jenkins.plugins.datadog.util.ShellCommandExecutor;

final class PythonConfigurator implements TracerConfigurator {

    private static final Logger LOGGER = Logger.getLogger(PythonConfigurator.class.getName());

    @Override
    public Map<String, String> configure(DatadogTracerJobProperty<?> tracerConfig, Node node, FilePath workspacePath, Map<String, String> envs) throws Exception {
        try {
            String pipVersion = workspacePath.act(new GetPipVersion());
            LOGGER.log(Level.FINE, "Got pip version " + pipVersion + " from " + workspacePath + " on " + node);

            String installTracerOutput = workspacePath.act(new InstallTracer());
            LOGGER.log(Level.FINE, "Trace installed in " + workspacePath + " on " + node + "; output: " + installTracerOutput);

            String tracerLocation = getTracerLocation(workspacePath);

            Map<String, String> variables = new HashMap<>();
            variables.put("PYTEST_ADDOPTS", PropertyUtils.prepend(envs, "PYTEST_ADDOPTS", "--ddtrace"));
            variables.put("PYTHONPATH", PropertyUtils.prepend(envs, "PYTHONPATH", tracerLocation + ":"));
            return variables;

        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Exception while trying to get node version from " + workspacePath + " on " + node + "; assuming node is not installed", e);
            return Collections.emptyMap();
        }
    }

    private static String getTracerLocation(FilePath workspacePath) throws IOException, InterruptedException {
        String getTracerLocationOutput = workspacePath.act(new GetTracerLocation());
        for (String line : getTracerLocationOutput.split("\n")) {
            if (line.contains("Location")) {
                return line.substring(line.indexOf(':') + 2);
            }
        }
        throw new IllegalStateException("Could not determine tracer location in " + workspacePath + "; command output is: " + getTracerLocationOutput);
    }

    private static final class GetPipVersion extends MasterToSlaveFileCallable<String> {
        @Override
        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                ShellCommandExecutor shellCommandExecutor = new ShellCommandExecutor(workspace, 30_000);
                return shellCommandExecutor.executeCommand(new ShellCommandExecutor.ToStringOutputParser(), "pip", "-V");
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
                return shellCommandExecutor.executeCommand(new ShellCommandExecutor.ToStringOutputParser(), "pip", "install", "-U", "ddtrace");
            } catch (IOException | InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    private static final class GetTracerLocation extends MasterToSlaveFileCallable<String> {
        @Override
        public String invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                ShellCommandExecutor shellCommandExecutor = new ShellCommandExecutor(workspace, 300_000);
                return shellCommandExecutor.executeCommand(new ShellCommandExecutor.ToStringOutputParser(), "pip", "show", "ddtrace");
            } catch (IOException | InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
