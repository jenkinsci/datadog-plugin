package org.datadog.jenkins.plugins.datadog.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class NonBlockingHttpClient implements HttpClient {

    private static final int DEFAULT_TIMEOUT_MS = 10 * 1000;
    private static final int SIZE_SPANS_SEND_BUFFER = 100;

    private static final Logger logger = Logger.getLogger(NonBlockingHttpClient.class.getName());

    private static final HttpErrorHandler NO_OP_HANDLER = new HttpErrorHandler() {
        @Override public void handle(final Exception e) { /* No-op */ }
    };

    private final HttpErrorHandler errorHandler;
    private final HttpSender sender;
    private final Map<PayloadMessage.Type, HttpMessageFactory> messageFactoryByType;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        final ThreadFactory delegate = Executors.defaultThreadFactory();
        @Override public Thread newThread(final Runnable r) {
            final Thread result = delegate.newThread(r);
            result.setName("DDNonBlockingHttpClient-" + result.getName());
            result.setDaemon(true);
            return result;
        }
    });

    private NonBlockingHttpClient(final Builder builder) {
        final int queueSize = builder.queueSize != null ? builder.queueSize : Integer.MAX_VALUE;
        final int httpTimeoutMs = builder.httpTimeoutMs != null ? builder.httpTimeoutMs : DEFAULT_TIMEOUT_MS;
        this.errorHandler = builder.errorHandler != null ? builder.errorHandler : NO_OP_HANDLER;
        this.messageFactoryByType = builder.messageFactoryByType;
        this.sender = createSender(queueSize, errorHandler, httpTimeoutMs);
        executor.submit(sender);

        if(this.messageFactoryByType != null) {
            for(Map.Entry<PayloadMessage.Type, HttpMessageFactory> messageFactoryEntry : messageFactoryByType.entrySet()) {
                logger.info(messageFactoryEntry.getKey() + " -> " + messageFactoryEntry.getValue().getURL());
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private HttpSender createSender(final int queueSize, final HttpErrorHandler errorHandler, final int httpTimeoutMs) {
        return new HttpSender(queueSize, errorHandler, httpTimeoutMs);
    }

    public void send(List<PayloadMessage> messages) {
        if(messages != null && !messages.isEmpty()) {
            final List<PayloadMessage> spanSendBuffer = new ArrayList<>(SIZE_SPANS_SEND_BUFFER);
            for(int i = 0; i < messages.size(); i++) {
                spanSendBuffer.add(messages.get(i));

                // Send every 100 spans or the last one.
                if(spanSendBuffer.size() == SIZE_SPANS_SEND_BUFFER || i == (messages.size() - 1)) {
                    final List<PayloadMessage> buffer = Collections.unmodifiableList(spanSendBuffer);
                    // We assume all payload messages belong to the same message type for now.
                    final PayloadMessage.Type type = buffer.get(0).getMessageType();
                    final HttpMessage message = this.messageFactoryByType.get(type).create(buffer);
                    this.sender.send(message);
                    spanSendBuffer.clear();
                }
            }
        }
    }

    @Override
    public void stop() {
        try {
            sender.shutdown();
            executor.shutdown();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
                if (!executor.isTerminated()) {
                    executor.shutdownNow();
                }
            } catch (Exception e) {
                errorHandler.handle(e);
                if (!executor.isTerminated()) {
                    executor.shutdownNow();
                }
            }
        } catch (final Exception e) {
            errorHandler.handle(e);
        }
    }

    @Override
    public void close() {
        stop();
    }

    public static class Builder {

        private HttpErrorHandler errorHandler;
        private Integer queueSize;
        private Integer httpTimeoutMs;
        private Map<PayloadMessage.Type, HttpMessageFactory> messageFactoryByType = new HashMap<>();

        public Builder errorHandler(final HttpErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public Builder queueSize(final int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder messageRoute(final PayloadMessage.Type key, final HttpMessageFactory messageFactory) {
            this.messageFactoryByType.put(key, messageFactory);
            return this;
        }

        public Builder httpTimeoutMs(final int httpTimeoutMs) {
            this.httpTimeoutMs = httpTimeoutMs;
            return this;
        }

        public NonBlockingHttpClient build() {
            return new NonBlockingHttpClient(this);
        }

    }
}
