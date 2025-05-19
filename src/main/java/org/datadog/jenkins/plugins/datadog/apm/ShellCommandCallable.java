package org.datadog.jenkins.plugins.datadog.apm;

import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import jenkins.MasterToSlaveFileCallable;
import org.datadog.jenkins.plugins.datadog.util.ShellCommandExecutor;

public class ShellCommandCallable extends MasterToSlaveFileCallable<String> {
    private final Map<String, String> environment;
    private final long timeoutMillis;
    private final String[] command;

    public ShellCommandCallable(Map<String, String> environment, long timeoutMillis, String... command) {
        this.environment = environment;
        this.timeoutMillis = timeoutMillis;
        this.command = Arrays.copyOf(command, command.length);
    }

    @Override
    public String invoke(File workspace, VirtualChannel channel) throws IOException {
        try {
            ShellCommandExecutor shellCommandExecutor = new ShellCommandExecutor(workspace, environment, timeoutMillis);
            return shellCommandExecutor.executeCommand(new ShellCommandExecutor.ToStringOutputParser(), command);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for shell command: " + Arrays.toString(command));

        } catch (Exception e) {
            throw new IOException("Error running shell command: " + Arrays.toString(command), e);
        }
    }
}
