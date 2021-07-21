package org.datadog.jenkins.plugins.datadog.transport;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getHttpURLConnection;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
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
    private volatile boolean shutdown;

    HttpSender(final int queueSize, final HttpErrorHandler errorHandler) {
        this(new LinkedBlockingQueue<HttpMessage>(queueSize), errorHandler);
    }

    HttpSender(final BlockingQueue<HttpMessage> queue, final HttpErrorHandler errorHandler) {
        this.queue = queue;
        this.errorHandler = errorHandler;
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
        while (!(queue.isEmpty() && shutdown)) {
            try {
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

    private void blockingSend(HttpMessage message) {
        HttpURLConnection conn = null;
        boolean status = true;
        try {
            conn = getHttpURLConnection(message.getURL());
            conn.setRequestMethod(message.getMethod().name());
            conn.setRequestProperty("Content-Type", message.getContentType());
            conn.setUseCaches(false);
            conn.setDoOutput(true);

            final byte[] payload = message.getPayload();
            final OutputStream outputStream = conn.getOutputStream();
            outputStream.write(payload);
            outputStream.close();

            int httpStatus = conn.getResponseCode();
            if(httpStatus >= 400) {
                logger.severe("Failed to send HTTP request: "+message.getMethod()+" "+ message.getURL()+ " - Status: HTTP "+conn.getResponseCode());
                status = false;
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
            status = false;
        } finally {
            if(conn != null) {
                conn.disconnect();
            }
        }
    }



    boolean isShutdown() {
        return shutdown;
    }

    void shutdown() {
        shutdown = true;
    }
}
