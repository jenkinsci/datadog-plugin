package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import jenkins.MasterToSlaveFileCallable;
import org.datadog.jenkins.plugins.datadog.util.ShellCommandExecutor;

public class ShellCommandCallable extends MasterToSlaveFileCallable<String> {
    private final long timeoutMillis;
    private final String[] command;

    public ShellCommandCallable(long timeoutMillis, String... command) {
        this.timeoutMillis = timeoutMillis;
        this.command = command;
    }

    @Override
    public String invoke(File workspace, VirtualChannel channel) throws IOException {
        try {
            ShellCommandExecutor shellCommandExecutor = new ShellCommandExecutor(workspace, timeoutMillis);
            return shellCommandExecutor.executeCommand(new ShellCommandExecutor.ToStringOutputParser(), command);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for shell command: " + Arrays.toString(command));

        } catch (Exception e) {
            throw new IOException("Error running shell command: " + Arrays.toString(command), e);
        }
    }
}
