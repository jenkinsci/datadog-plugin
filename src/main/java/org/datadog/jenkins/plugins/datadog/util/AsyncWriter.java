package org.datadog.jenkins.plugins.datadog.util;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class AsyncWriter<T> {

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
        if (element != null && !queue.offer(element, submitTimeoutSeconds, TimeUnit.SECONDS)) {
            throw new TimeoutException("Timed out while submitting span: " + name);
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

                List<T> elements = new ArrayList<>(batchSizeLimit);
                elements.add(element);
                queue.drainTo(elements, batchSizeLimit - 1);

                queueConsumer.accept(elements);

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
