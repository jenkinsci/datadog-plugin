package org.datadog.jenkins.plugins.datadog.logs;

import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import java.util.logging.Logger;

public class DatadogWriterConsumer implements Runnable{
    private DatadogWriterBuffer buffer;
    private DatadogClient client;
    private static final Logger logger = Logger.getLogger(DatadogWriter.class.getName());

    public DatadogWriterConsumer(DatadogWriterBuffer buffer, DatadogClient client) {
        this.buffer = buffer;
        this.client = client;
    }

    @Override
    public void run() {
        try {
            while (true) {
                JSONObject payload = buffer.getPayload();

                // if done
                if (payload == null) {
                    return;
                }

                if (client == null) {
                    DatadogUtilities.severe(logger, null, "Datadog Client is null.");
                }

                boolean status = client.sendLogs(payload.toString());
                if (!status) {
                    // we try again in case a connection has to be re-established.
                    DatadogUtilities.severe(logger, null, "Datadog failed sending logs but will try one more time.");
                    client.sendLogs(payload.toString());
                }
            }
        } catch (Exception e) {
            DatadogUtilities.severe(logger, null, "Unable to get logs from buffer.");
        }
    }
}
