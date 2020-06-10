package org.datadog.jenkins.plugins.datadog.logs;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

public class DatadogDecorator extends TaskListenerDecorator implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DatadogDecorator.class.getName());
    private transient Run<?, ?> run;

    private DatadogDecorator(WorkflowRun run) {
        this.run = run;
    }

    @Nonnull
    @Override
    public OutputStream decorate(@Nonnull OutputStream outputStream) {
        DatadogWriter writer = new DatadogWriter(run, outputStream, run.getCharset());
        return new DatadogOutputStream(outputStream, writer);
    }

    @Extension
    public static final class Factory implements TaskListenerDecorator.Factory {

        @Override
        @Nullable
        public TaskListenerDecorator of(@Nonnull FlowExecutionOwner owner) {
            if (DatadogUtilities.getDatadogGlobalDescriptor() == null ||
                    !DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildLogs()) {
                return null;
            }
            try {
                Queue.Executable executable = owner.getExecutable();
                if (executable instanceof WorkflowRun) {
                    return new DatadogDecorator((WorkflowRun) executable);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, null, ex);
            }
            return null;
        }
    }
}