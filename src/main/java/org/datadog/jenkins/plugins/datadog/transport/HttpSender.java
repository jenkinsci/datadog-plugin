package org.datadog.jenkins.plugins.datadog.transport;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;

public class HttpSender implements Runnable {

    private static final Logger logger = Logger.getLogger(HttpSender.class.getName());

    private final BlockingQueue<HttpMessage> queue;
    private final HttpErrorHandler errorHandler;
    private final HttpClient client;

    private volatile boolean shutdown;

    HttpSender(final int queueSize, final HttpErrorHandler errorHandler, final int httpTimeoutMs) {
        this(new ArrayBlockingQueue<>(queueSize), errorHandler, httpTimeoutMs);
    }

    HttpSender(final BlockingQueue<HttpMessage> queue, final HttpErrorHandler errorHandler, final int httpTimeoutMs) {
        this.queue = queue;
        this.errorHandler = errorHandler;
        this.client = new HttpClient(httpTimeoutMs);
    }

    boolean send(final HttpMessage message){
        if (shutdown) {
            return false;
        }
        try {
            queue.put(message);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void run() {
        // Consumer loop.
        // Consume till shutdown=true and queue is empty.
        while (!queue.isEmpty() || !shutdown) {
            try {
                // Try to retrieve (and remove) the head of the queue
                // with 1 second of timeout to avoid blocking
                // the thread indefinitely.
                final HttpMessage message = queue.poll(1, TimeUnit.SECONDS);
                if (null != message) {
                    process(message);
                }
            } catch (final InterruptedException e) {
                if (shutdown) {
                    return;
                }
            } catch (final Exception e) {
                errorHandler.handle(e);
            }
        }
    }

    protected void process(HttpMessage message) {
        try {
            client.sendAsynchronously(message);
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Error while sending message: " + message);
        }
    }

    void shutdown() {
        shutdown = true;
    }
}
