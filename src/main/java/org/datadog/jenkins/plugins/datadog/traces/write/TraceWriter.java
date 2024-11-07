package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.util.AsyncWriter;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public final class TraceWriter {

    private static final String QUEUE_CAPACITY_ENV_VAR = "DD_JENKINS_TRACES_QUEUE_CAPACITY";
    private static final String SUBMIT_TIMEOUT_ENV_VAR = "DD_JENKINS_TRACES_SUBMIT_TIMEOUT_SECONDS";
    private static final String STOP_TIMEOUT_ENV_VAR = "DD_JENKINS_TRACES_STOP_TIMEOUT_SECONDS";
    private static final String POLLING_TIMEOUT_ENV_VAR = "DD_JENKINS_TRACES_POLLING_TIMEOUT_SECONDS";
    private static final String BATCH_SIZE_LIMIT_ENV_VAR = "DD_JENKINS_TRACES_BATCH_SIZE_LIMIT";
    private static final int DEFAULT_QUEUE_CAPACITY = 10_000;
    private static final int DEFAULT_SUBMIT_TIMEOUT_SECONDS = 0;
    private static final int DEFAULT_STOP_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_POLLING_TIMEOUT_SECONDS = 5;
    private static final int DEFAULT_BATCH_SIZE_LIMIT = 100;

    private final TraceWriteStrategy traceWriteStrategy;
    private final AsyncWriter<Payload> asyncWriter;

    public TraceWriter(DatadogClient datadogClient) {
        this.traceWriteStrategy = datadogClient.createTraceWriteStrategy();
        this.asyncWriter = new AsyncWriter<>("DD-Trace-Writer",
                traceWriteStrategy::send,
                traceWriteStrategy::close,
                DatadogUtilities.envVar(QUEUE_CAPACITY_ENV_VAR, DEFAULT_QUEUE_CAPACITY),
                DatadogUtilities.envVar(SUBMIT_TIMEOUT_ENV_VAR, DEFAULT_SUBMIT_TIMEOUT_SECONDS),
                DatadogUtilities.envVar(POLLING_TIMEOUT_ENV_VAR, DEFAULT_POLLING_TIMEOUT_SECONDS),
                DatadogUtilities.envVar(STOP_TIMEOUT_ENV_VAR, DEFAULT_STOP_TIMEOUT_SECONDS),
                DatadogUtilities.envVar(BATCH_SIZE_LIMIT_ENV_VAR, DEFAULT_BATCH_SIZE_LIMIT));
    }

    public void start() {
        asyncWriter.start();
    }

    public void stopAsynchronously() {
        asyncWriter.stopAsynchronously();
    }

    public void stopSynchronously() throws InterruptedException {
        asyncWriter.stopSynchronously();
    }

    public void submitBuild(final BuildData buildData, final Run<?,?> run) throws InterruptedException, TimeoutException {
        asyncWriter.submit(traceWriteStrategy.serialize(buildData, run));
    }

    public void submitPipelineStep(PipelineStepData stepData, Run<?, ?> run) throws InterruptedException, TimeoutException, IOException {
        asyncWriter.submit(traceWriteStrategy.serialize(stepData, run));
    }
}
