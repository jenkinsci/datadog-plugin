package org.datadog.jenkins.plugins.datadog.traces.write;

import javax.annotation.Nullable;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;

public class TraceWriterFactory {

    private static volatile TraceWriter TRACE_WRITER;

    public static synchronized void onDatadogClientUpdate(@Nullable DatadogClient client) {
        if (client == null) {
            return;
        }

        if (TRACE_WRITER != null) {
            TRACE_WRITER.stop();
        }

        TRACE_WRITER = new TraceWriter(client);
    }

    @Nullable
    public static TraceWriter getTraceWriter() {
        if (TRACE_WRITER == null) {
            onDatadogClientUpdate(ClientFactory.getClient());
        }
        return TRACE_WRITER;
    }
}
