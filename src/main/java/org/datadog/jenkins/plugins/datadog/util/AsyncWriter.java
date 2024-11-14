package org.datadog.jenkins.plugins.datadog.util;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

public final class AsyncWriter<T> {

    public static final MetricRegistry METRICS = new MetricRegistry();

    private static final Logger logger = Logger.getLogger(AsyncWriter.class.getName());

    private final Consumer<List<T>> queueConsumer;
    private final Runnable onStop;
    private final BlockingQueue<T> queue;
    private final Thread poller;

    private final String name;
    private final int submitTimeoutSeconds;
    private final int pollingTimeoutSeconds;
    private final int stopTimeoutSeconds;
    private final int batchSizeLimit;

    private final Timer submit;
    private final Meter submitDropped;
    private final Timer dispatch;
    private final Gauge<Integer> queueSize;
    private final Histogram batchSize;

    public AsyncWriter(String name,
                       Consumer<List<T>> queueConsumer,
                       Runnable onStop,
                       int queueCapacity,
                       int submitTimeoutSeconds,
                       int pollingTimeoutSeconds,
                       int stopTimeoutSeconds,
                       int batchSizeLimit) {
        this.queueConsumer = queueConsumer;
        this.onStop = onStop;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.poller = new Thread(this::runPollingLoop, name);
        this.name = name;
        this.submitTimeoutSeconds = submitTimeoutSeconds;
        this.pollingTimeoutSeconds = pollingTimeoutSeconds;
        this.stopTimeoutSeconds = stopTimeoutSeconds;
        this.batchSizeLimit = batchSizeLimit;
        this.submit = METRICS.timer(name + ".submit");
        this.submitDropped = METRICS.meter(name + ".submit.dropped");
        this.dispatch = METRICS.timer(name + ".dispatch");
        this.queueSize = METRICS.gauge(name + ".queue.size", () -> queue::size);
        this.batchSize = METRICS.histogram(name + ".batch.size");
    }

    public void start() {
        poller.start();
    }

    public void stopAsynchronously() {
        poller.interrupt();
        onStop.run();
    }

    public void stopSynchronously() throws InterruptedException {
        poller.interrupt();
        poller.join(TimeUnit.SECONDS.toMillis(stopTimeoutSeconds));
        onStop.run();
    }

    public void submit(@Nullable T element) throws InterruptedException, TimeoutException {
        if (element == null) {
            return;
        }
        try (Timer.Context submitTime = submit.time()) {
            if (!queue.offer(element, submitTimeoutSeconds, TimeUnit.SECONDS)) {
                submitDropped.mark();
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Timed out while doing async submit: " + name);
                }
            }
        }
    }

    private void runPollingLoop() {
        long stopPollingAt = Long.MAX_VALUE;
        while (System.currentTimeMillis() < stopPollingAt) {
            try {
                T element = queue.poll(pollingTimeoutSeconds, TimeUnit.SECONDS);
                if (element == null) {
                    // nothing to send
                    continue;
                }

                try (Timer.Context dispatchTime = dispatch.time()) {
                    List<T> elements = new ArrayList<>(batchSizeLimit);
                    elements.add(element);
                    queue.drainTo(elements, batchSizeLimit - 1);
                    queueConsumer.accept(elements);

                    batchSize.update(elements.size());
                }

            } catch (InterruptedException e) {
                logger.info("Queue poller thread interrupted: " + name);
                stopPollingAt = Math.min(stopPollingAt, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(stopTimeoutSeconds));

            } catch (Exception e) {
                DatadogUtilities.severe(logger, e, "Error while consuming data from queue: " + name);
            }
        }
        logger.info("Queue polling stopped, elements not flushed " + queue.size() + ": " + name);
    }

}
