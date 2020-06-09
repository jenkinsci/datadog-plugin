package org.datadog.jenkins.plugins.datadog.pipeline;

import hudson.console.ConsoleNote;
import hudson.console.LineTransformationOutputStream;
import hudson.model.Run;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

public class DatadogDecorator extends TaskListenerDecorator {

    private static final Logger LOGGER = Logger.getLogger(DatadogDecorator.class.getName());
    private transient Run<?, ?> run;

    public DatadogDecorator(WorkflowRun run) {
        LOGGER.log(Level.INFO, "Creating decorator for {0}", run.toString());
    }

    @Override
    public OutputStream decorate(OutputStream logger) throws IOException, InterruptedException {
        DatadogOutputStream out = new DatadogOutputStream(logger);
        return out;
    }

    class DatadogOutputStream extends LineTransformationOutputStream {

        private final OutputStream delegate;

        public DatadogOutputStream(OutputStream delegate){
            this.delegate = delegate;
        }
        @Override
        protected void eol(byte[] b, int len) throws IOException {
            delegate.write(b, 0, len);
            this.flush();
            String line = new String(b, 0, len, StandardCharsets.UTF_8);
            line = ConsoleNote.removeNotes(line);
            LOGGER.severe("Decorator: " + line);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
            super.flush();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() throws IOException {
            delegate.close();
            super.close();
        }
    }
}
