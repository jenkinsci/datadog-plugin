package org.datadog.jenkins.plugins.datadog.transport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class NonBlockingHttpClient implements HttpClient {

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
        this.errorHandler = builder.errorHandler != null ? builder.errorHandler : NO_OP_HANDLER;
        this.messageFactoryByType = builder.messageFactoryByType;
        this.sender = createSender(queueSize, errorHandler);
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

    private HttpSender createSender(final int queueSize, final HttpErrorHandler errorHandler) {
        return new HttpSender(queueSize, errorHandler);
    }

    public void send(List<PayloadMessage> messages) {
        if(messages != null && !messages.isEmpty()) {
            // We assume all payload messages belong to the same message type for now.
            final PayloadMessage.Type type = messages.get(0).getMessageType();
            final HttpMessage message = this.messageFactoryByType.get(type).create(messages);
            this.sender.send(message);
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

        public NonBlockingHttpClient build() {
            return new NonBlockingHttpClient(this);
        }

    }
}
