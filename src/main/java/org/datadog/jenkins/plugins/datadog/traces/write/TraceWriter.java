package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

public class TraceWriter {

    private static final Logger logger = Logger.getLogger(TraceWriter.class.getName());

    private static final String QUEUE_CAPACITY_ENV_VAR = "DD_JENKINS_TRACES_QUEUE_CAPACITY";
    private static final String SUBMIT_TIMEOUT_ENV_VAR = "DD_JENKINS_TRACES_SUBMIT_TIMEOUT_SECONDS";
    private static final String STOP_TIMEOUT_ENV_VAR = "DD_JENKINS_TRACES_STOP_TIMEOUT_SECONDS";
    private static final String POLLING_INTERVAL_ENV_VAR = "DD_JENKINS_TRACES_POLLING_INTERVAL_SECONDS";
    private static final String BATCH_SIZE_LIMIT_ENV_VAR = "DD_JENKINS_TRACES_BATCH_SIZE_LIMIT";
    private static final int DEFAULT_QUEUE_CAPACITY = 10_000;
    private static final int DEFAULT_SUBMIT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_STOP_TIMEOUT_SECONDS = 15;
    private static final int DEFAULT_POLLING_INTERVAL_SECONDS = 10;
    private static final int DEFAULT_BATCH_SIZE_LIMIT = 100;

    private final TraceWriteStrategy traceWriteStrategy;
    private final BlockingQueue<JSONObject> queue;
    private final Thread poller;

    public TraceWriter(DatadogClient datadogClient) {
        this.traceWriteStrategy = datadogClient.createTraceWriteStrategy();

        this.queue = new ArrayBlockingQueue<>(getEnv(QUEUE_CAPACITY_ENV_VAR, DEFAULT_QUEUE_CAPACITY));

        this.poller = new Thread(new QueuePoller(traceWriteStrategy, queue), "DD-Trace-Writer");
        this.poller.setDaemon(true);
        this.poller.start();
    }

    public void submitBuild(final BuildData buildData, final Run<?,?> run) throws InterruptedException, TimeoutException {
        JSONObject buildJson = traceWriteStrategy.serialize(buildData, run);
        submit(buildJson);
    }

    public void submitPipeline(FlowNode flowNode, Run<?, ?> run) throws InterruptedException, TimeoutException {
        Collection<JSONObject> nodeJsons = traceWriteStrategy.serialize(flowNode, run);
        for (JSONObject nodeJson : nodeJsons) {
            submit(nodeJson);
        }
    }

    private void submit(JSONObject json) throws InterruptedException, TimeoutException {
        if (!queue.offer(json, getEnv(SUBMIT_TIMEOUT_ENV_VAR, DEFAULT_SUBMIT_TIMEOUT_SECONDS), TimeUnit.SECONDS)) {
            throw new TimeoutException("Timed out while submitting span");
        }
    }

    public void stop() {
        poller.interrupt();
    }

    private static final class QueuePoller implements Runnable {
        private final TraceWriteStrategy traceWriteStrategy;
        private final BlockingQueue<JSONObject> queue;

        public QueuePoller(TraceWriteStrategy traceWriteStrategy, BlockingQueue<JSONObject> queue) {
            this.traceWriteStrategy = traceWriteStrategy;
            this.queue = queue;
        }

        @Override
        public void run() {
            long shutdownAt = Long.MAX_VALUE;
            while (System.currentTimeMillis() < shutdownAt) {
                try {
                    JSONObject span = queue.poll(getEnv(POLLING_INTERVAL_ENV_VAR, DEFAULT_POLLING_INTERVAL_SECONDS), TimeUnit.SECONDS);
                    if (span == null) {
                        continue; // nothing to send
                    }

                    int batchSize = getEnv(BATCH_SIZE_LIMIT_ENV_VAR, DEFAULT_BATCH_SIZE_LIMIT);
                    List<JSONObject> spans = new ArrayList<>(batchSize);
                    spans.add(span);
                    queue.drainTo(spans, batchSize - 1);

                    traceWriteStrategy.send(spans);

                } catch (InterruptedException e) {
                    logger.info("Queue poller thread interrupted");
                    shutdownAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(getEnv(STOP_TIMEOUT_ENV_VAR, DEFAULT_STOP_TIMEOUT_SECONDS));

                } catch (Exception e) {
                    DatadogUtilities.severe(logger, e, "Error while sending trace");
                }
            }
            logger.info("Queue poller thread shut down");
        }
    }

    private static int getEnv(String envVar, int defaultValue) {
        String value = System.getenv(envVar);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                DatadogUtilities.severe(logger, null, "Invalid value " + value + " provided for env var " + envVar + ": integer number expected");
            }
        }
        return defaultValue;
    }
}
