package org.datadog.jenkins.plugins.datadog.transport;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getHttpURLConnection;

import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class HttpSender implements Runnable {

    private static final Logger logger = Logger.getLogger(HttpSender.class.getName());

    private final BlockingQueue<HttpMessage> queue;
    private final HttpErrorHandler errorHandler;
    private final int httpTimeoutMs;

    private volatile boolean shutdown;

    HttpSender(final int queueSize, final HttpErrorHandler errorHandler, final int httpTimeoutMs) {
        this(new LinkedBlockingQueue<HttpMessage>(queueSize), errorHandler, httpTimeoutMs);
    }

    HttpSender(final BlockingQueue<HttpMessage> queue, final HttpErrorHandler errorHandler, final int httpTimeoutMs) {
        this.queue = queue;
        this.errorHandler = errorHandler;
        this.httpTimeoutMs = httpTimeoutMs;
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    boolean send(final HttpMessage message){
        if(!shutdown){
            queue.offer(message);
            return true;
        }
        return false;
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
                if(null != message) {
                    blockingSend(message);
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

    protected void blockingSend(HttpMessage message) {
        HttpURLConnection conn = null;
        try {
            conn = getHttpURLConnection(message.getURL(), httpTimeoutMs);
            conn.setRequestMethod(message.getMethod().name());
            conn.setRequestProperty("Content-Type", message.getContentType());
            conn.setUseCaches(false);
            conn.setDoOutput(true);

            final byte[] payload = message.getPayload();
            final OutputStream outputStream = conn.getOutputStream();
            outputStream.write(payload);
            outputStream.close();

            int httpStatus = conn.getResponseCode();
            logger.fine("HTTP/"+message.getMethod()+" " + message.getURL() + " ["+payload.length+" bytes] --> HTTP " + httpStatus);
            if(httpStatus >= 400) {
                logger.severe("Failed to send HTTP request: "+message.getMethod()+" "+ message.getURL()+ " - Status: HTTP "+httpStatus);
            }
        } catch (Exception ex) {
            errorHandler.handle(ex);
            try {
                if(conn != null) {
                    logger.severe("Failed to send HTTP request: "+message.getMethod()+" "+ message.getURL()+ " - Status: HTTP "+conn.getResponseCode());
                }
            } catch (IOException ioex) {
                errorHandler.handle(ioex);
            }
        } finally {
            if(conn != null) {
                conn.disconnect();
            }
        }
    }



    void shutdown() {
        shutdown = true;
    }
}
