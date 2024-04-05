package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.init.Terminator;
import javax.annotation.Nullable;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;

public class TraceWriterFactory {

    private static volatile TraceWriter TRACE_WRITER;

    public static synchronized void onDatadogClientUpdate(@Nullable DatadogClient client) {
        if (client == null) {
            return;
        }

        if (TRACE_WRITER != null) {
            TRACE_WRITER.stopAsynchronously();
        }

        TRACE_WRITER = new TraceWriter(client);
        TRACE_WRITER.start();
    }

    /**
     * This method is called when the plugin is stopped.
     * If writer is initialized, it will be stopped synchronously.
     */
    @Terminator
    public static synchronized void stop() throws InterruptedException {
        if (TRACE_WRITER != null) {
            TRACE_WRITER.stopSynchronously();
            TRACE_WRITER = null;
        }
    }

    @Nullable
    public static TraceWriter getTraceWriter() {
        if (TRACE_WRITER == null) {
            onDatadogClientUpdate(ClientHolder.getClient());
        }
        return TRACE_WRITER;
    }
}
