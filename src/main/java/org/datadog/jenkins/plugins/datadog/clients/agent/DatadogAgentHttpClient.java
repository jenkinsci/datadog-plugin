package org.datadog.jenkins.plugins.datadog.clients.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class DatadogAgentHttpClient implements AgentHttpClient {

    private static final AgentHttpErrorHandler NO_OP_HANDLER = new AgentHttpErrorHandler() {
        @Override public void handle(final Exception e) { /* No-op */ }
    };

    private final AgentHttpErrorHandler errorHandler;
    private final DatadogAgentHttpSender sender;
    private final Map<MessageType, DatadogAgentHttpMessageFactory> messageFactoryByType;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        final ThreadFactory delegate = Executors.defaultThreadFactory();
        @Override public Thread newThread(final Runnable r) {
            final Thread result = delegate.newThread(r);
            result.setName("DDAgentHttp-" + result.getName());
            result.setDaemon(true);
            return result;
        }
    });

    private DatadogAgentHttpClient(final Builder builder) {
        try {
            this.errorHandler = builder.errorHandler != null ? builder.errorHandler : NO_OP_HANDLER;
            final int queueSize = builder.queueSize != null ? builder.queueSize : Integer.MAX_VALUE;
            this.messageFactoryByType = builder.messageFactoryByType;
            this.sender = createSender(queueSize, errorHandler);
            executor.submit(sender);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected DatadogAgentHttpSender createSender(final int queueSize, final AgentHttpErrorHandler errorHandler) {
        return new DatadogAgentHttpSender(queueSize, errorHandler);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void send(AgentMessage msg) {
        final HttpMessage message = this.messageFactoryByType.get(msg.getMessageType()).create(msg);
        this.sender.send(message);
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

        private AgentHttpErrorHandler errorHandler;
        private Integer queueSize;
        private Map<MessageType, DatadogAgentHttpMessageFactory> messageFactoryByType = new HashMap<>();

        public Builder errorHandler(final AgentHttpErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public Builder queueSize(final int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder messageFactory(final MessageType key, final DatadogAgentHttpMessageFactory messageFactory) {
            this.messageFactoryByType.put(key, messageFactory);
            return this;
        }

        public DatadogAgentHttpClient build() {
            return new DatadogAgentHttpClient(this);
        }

    }
}
