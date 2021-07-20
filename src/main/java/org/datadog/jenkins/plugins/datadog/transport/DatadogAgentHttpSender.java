package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.transport.message.HttpMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DatadogAgentHttpSender implements Runnable {

    private final BlockingQueue<HttpMessage> queue;
    private final AgentHttpErrorHandler errorHandler;
    private volatile boolean shutdown;

    DatadogAgentHttpSender(final int queueSize, final AgentHttpErrorHandler errorHandler) {
        this(new LinkedBlockingQueue<HttpMessage>(queueSize), errorHandler);
    }

    DatadogAgentHttpSender(final BlockingQueue<HttpMessage> queue, final AgentHttpErrorHandler errorHandler) {
        this.queue = queue;
        this.errorHandler = errorHandler;
    }

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
            conn = (HttpURLConnection) message.getURL().openConnection();
            int timeoutMS = 1 * 60 * 1000;
            conn.setConnectTimeout(timeoutMS);
            conn.setReadTimeout(timeoutMS);
            conn.setRequestMethod(message.getMethod());
            conn.setRequestProperty("Content-Type", message.getContentType());
            conn.setUseCaches(false);
            conn.setDoOutput(true);

            final byte[] payload = message.getPayload();
            final OutputStream outputStream = conn.getOutputStream();
            outputStream.write(payload);
            outputStream.close();

            int httpStatus = conn.getResponseCode();
            System.out.println(""+httpStatus);

        } catch (Exception ex) {
            System.out.println("--- EXCEPTION: " + ex);
            try {
                if(conn != null) {
                    System.out.println("--- Failed HTTP Status: " + conn.getResponseCode());
                }
            } catch (IOException ioex) {
                System.out.println("--- Failed to inspect HTTP response");
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
