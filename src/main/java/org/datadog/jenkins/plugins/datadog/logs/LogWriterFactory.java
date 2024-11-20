package org.datadog.jenkins.plugins.datadog.logs;

import hudson.init.Terminator;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.util.AsyncWriter;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import javax.annotation.Nullable;

public class LogWriterFactory {

    private static final String QUEUE_CAPACITY_ENV_VAR = "DD_JENKINS_LOGS_QUEUE_CAPACITY";
    private static final String SUBMIT_TIMEOUT_ENV_VAR = "DD_JENKINS_LOGS_SUBMIT_TIMEOUT_SECONDS";
    private static final String STOP_TIMEOUT_ENV_VAR = "DD_JENKINS_LOGS_STOP_TIMEOUT_SECONDS";
    private static final String POLLING_TIMEOUT_ENV_VAR = "DD_JENKINS_LOGS_POLLING_TIMEOUT_SECONDS";
    private static final String BATCH_SIZE_LIMIT_ENV_VAR = "DD_JENKINS_LOGS_BATCH_SIZE_LIMIT";

    private static final int DEFAULT_QUEUE_CAPACITY = 10_000;
    private static final int DEFAULT_SUBMIT_TIMEOUT_SECONDS = 0;
    private static final int DEFAULT_STOP_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_POLLING_TIMEOUT_SECONDS = 2;
    private static final int DEFAULT_BATCH_SIZE_LIMIT = 500;

    private static volatile AsyncWriter<JSONObject> LOG_WRITER;

    public static synchronized void onDatadogClientUpdate(@Nullable DatadogClient client) {
        if (client == null) {
            return;
        }

        if (LOG_WRITER != null) {
            LOG_WRITER.stopAsynchronously();
        }

        LogWriteStrategy logWriteStrategy = client.createLogWriteStrategy();
        LOG_WRITER = new AsyncWriter<>("DD-Log-Writer",
                logWriteStrategy::send,
                logWriteStrategy::close,
                DatadogUtilities.envVar(QUEUE_CAPACITY_ENV_VAR, DEFAULT_QUEUE_CAPACITY),
                DatadogUtilities.envVar(SUBMIT_TIMEOUT_ENV_VAR, DEFAULT_SUBMIT_TIMEOUT_SECONDS),
                DatadogUtilities.envVar(POLLING_TIMEOUT_ENV_VAR, DEFAULT_POLLING_TIMEOUT_SECONDS),
                DatadogUtilities.envVar(STOP_TIMEOUT_ENV_VAR, DEFAULT_STOP_TIMEOUT_SECONDS),
                DatadogUtilities.envVar(BATCH_SIZE_LIMIT_ENV_VAR, DEFAULT_BATCH_SIZE_LIMIT));
        LOG_WRITER.start();
    }

    /**
     * This method is called when the plugin is stopped.
     * If writer is initialized, it will be stopped synchronously.
     */
    @Terminator
    public static synchronized void stop() throws InterruptedException {
        if (LOG_WRITER != null) {
            LOG_WRITER.stopSynchronously();
            LOG_WRITER = null;
        }
    }

    @Nullable
    public static AsyncWriter<JSONObject> getLogWriter() {
        return LOG_WRITER;
    }
}
