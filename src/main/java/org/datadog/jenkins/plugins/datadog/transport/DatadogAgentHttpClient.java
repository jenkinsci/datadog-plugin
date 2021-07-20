package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;
import org.datadog.jenkins.plugins.datadog.transport.mapper.JsonTraceSpanMapper;
import org.datadog.jenkins.plugins.datadog.transport.message.DatadogAgentHttpTraceMessageFactory;
import org.datadog.jenkins.plugins.datadog.transport.message.HttpMessage;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class DatadogAgentHttpClient implements AgentHttpClient {

    private static final AgentHttpErrorHandler NO_OP_HANDLER = new AgentHttpErrorHandler() {
        @Override public void handle(final Exception e) { /* No-op */ }
    };

    private final AgentHttpErrorHandler errorHandler;
    private final AgentHttpMessageFactory<TraceSpan> httpMessageFactory;
    private final DatadogAgentHttpSender sender;

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
            final URL tracesURL = new URL("http://" + builder.agentHost + ":" + builder.traceAgentPort + "/v0.3/traces");
            final TraceSpanMapper tracerMapper = builder.mapper != null ? builder.mapper : new JsonTraceSpanMapper();
            this.errorHandler = builder.errorHandler != null ? builder.errorHandler : NO_OP_HANDLER;
            final int queueSize = builder.queueSize != null ? builder.queueSize : Integer.MAX_VALUE;
            this.httpMessageFactory = builder.httpMessageFactory != null ? builder.httpMessageFactory : new DatadogAgentHttpTraceMessageFactory(tracesURL, tracerMapper);

            this.sender = createSender(queueSize, errorHandler);
            executor.submit(sender);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected DatadogAgentHttpSender createSender(final int queueSize, final AgentHttpErrorHandler errorHandler) {
        return new DatadogAgentHttpSender(queueSize, errorHandler);
    }

    public static DatadogAgentHttpClient.Builder builder() {
        return new Builder();
    }

    public void send(TraceSpan span) {
        final HttpMessage message = httpMessageFactory.create(span);
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

        private String agentHost;
        private int traceAgentPort;
        private TraceSpanMapper mapper;
        private AgentHttpErrorHandler errorHandler;
        private AgentHttpMessageFactory httpMessageFactory;
        private Integer queueSize;

        public Builder agentHost(final String agentHost) {
            this.agentHost = agentHost;
            return this;
        }

        public Builder traceAgentPort(final int traceAgentPort) {
            this.traceAgentPort = traceAgentPort;
            return this;
        }

        public Builder traceSpanMapper(final TraceSpanMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        public Builder errorHandler(final AgentHttpErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public Builder queueSize(final int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder messageFactory(final AgentHttpMessageFactory messageFactory) {
            this.httpMessageFactory = messageFactory;
            return this;
        }

        public DatadogAgentHttpClient build() {
            return new DatadogAgentHttpClient(this);
        }

    }
}
