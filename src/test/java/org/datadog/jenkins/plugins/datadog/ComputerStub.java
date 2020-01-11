package org.datadog.jenkins.plugins.datadog;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.slaves.RetentionStrategy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.LogRecord;

public class ComputerStub extends Computer {

    public ComputerStub(Node node) {
        super(node);
    }

    @Nullable
    @Override
    public VirtualChannel getChannel() {
        return null;
    }

    @Override
    public Charset getDefaultCharset() {
        return null;
    }

    @Override
    public List<LogRecord> getLogRecords() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public void doLaunchSlaveAgent(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {

    }

    @Override
    protected Future<?> _connect(boolean forceReconnect) {
        return null;
    }

    @CheckForNull
    @Override
    public Boolean isUnix() {
        return null;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public RetentionStrategy getRetentionStrategy() {
        return null;
    }
}
