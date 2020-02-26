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
import hudson.console.ConsoleLogFilter;
import hudson.model.*;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.logging.Logger;

@Extension
public class DatadogConsoleLogFilter extends ConsoleLogFilter implements Serializable {

    private static final Logger logger = Logger.getLogger(DatadogConsoleLogFilter.class.getName());
    public transient Run<?, ?> run;
    private static final long serialVersionUID = 1L;

    public DatadogConsoleLogFilter() {
    }

    public DatadogConsoleLogFilter(Run<?, ?> run) {
        this.run = run;
    }

    public OutputStream decorateLogger(Run build, OutputStream outputStream) throws IOException, InterruptedException {
        try {
            if (DatadogUtilities.getDatadogGlobalDescriptor() == null ||
                    !DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildLogs()) {
                logger.fine("Log Collection disabled");
                return outputStream;
            }

            if (build != null) {
                DatadogWriter writer = new DatadogWriter(build, build.getCharset());
                return new DatadogOutputStream(outputStream, writer);
            } else if (run != null) {
                DatadogWriter writer = new DatadogWriter(run, run.getCharset());
                return new DatadogOutputStream(outputStream, writer);
            } else {
                return outputStream;
            }
        } catch (Exception e){
            DatadogUtilities.severe(logger, e, null);
        }
        return outputStream;
    }

    @Override
    public OutputStream decorateLogger(AbstractBuild abstractBuild, OutputStream outputStream) throws IOException, InterruptedException {
        return decorateLogger((Run) abstractBuild, outputStream);
    }

    @Override
    public OutputStream decorateLogger(@Nonnull Computer computer, OutputStream logger) throws IOException, InterruptedException {
        return logger;
    }
}
