/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */
package org.datadog.jenkins.plugins.datadog.logs;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

public class DatadogDecorator extends TaskListenerDecorator {

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