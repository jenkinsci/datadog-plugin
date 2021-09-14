package org.datadog.jenkins.plugins.datadog.traces.message;

import org.datadog.jenkins.plugins.datadog.traces.IdGenerator;
import org.datadog.jenkins.plugins.datadog.transport.PayloadMessage;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class TraceSpan implements PayloadMessage {

    public static final String PRIORITY_SAMPLING_KEY = "_sampling_priority_v1";

    private final TraceSpanContext traceSpanContext;
    private final String operationName;
    private String serviceName;
    private String resourceName;
    private boolean error;
    private String type;

    private final Map<String, String> meta = new HashMap<>();
    private final Map<String, Double> metrics = new HashMap<>();

    private final long startNano;
    private long durationNano;

    public TraceSpan(final String name, final long startNano) {
        this(name, startNano, null);
    }

    public TraceSpan(final String name, final long startNano, final TraceSpanContext spanContext) {
        this.operationName = name;
        this.startNano = startNano;
        this.traceSpanContext = spanContext != null ? spanContext : new TraceSpanContext();

        // Avoid sampling
        this.metrics.put(PRIORITY_SAMPLING_KEY, 1.0);
    }

    public TraceSpanContext context() {
        return traceSpanContext;
    }

    public void putMeta(String key, String value) {
        this.meta.put(key, value);
    }

    public void putMeta(String key, Number value) {
        this.meta.put(key, value.toString());
    }

    public void putMeta(String key, Boolean value) {
        this.meta.put(key, value.toString());
    }

    public void putMetric(String key, double value) {
        this.metrics.put(key, value);
    }

    public void setType(final String type) {
        this.type = type;
    }

    public void setResourceName(final String resourceName) {
        this.resourceName = resourceName;
    }

    public void setServiceName(final String serviceName) {
        this.serviceName = serviceName;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public void setEndNano(final long endNano) {
        this.durationNano = endNano - startNano;
    }

    public String getOperationName() {
        return this.operationName;
    }

    public String getResourceName() {
        return this.resourceName;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public String getType() {
        return this.type;
    }

    public Map<String, String> getMeta() {
        return this.meta;
    }

    public Map<String, Double> getMetrics() {
        return this.metrics;
    }

    public long getStartNano() {
        return this.startNano;
    }

    public long getDurationNano() {
        return this.durationNano;
    }

    public boolean isError() {
        return error;
    }

    @Override
    public PayloadMessage.Type getMessageType() {
        return PayloadMessage.Type.TRACE;
    }

    public static class TraceSpanContext implements Serializable {

        private static final long serialVersionUID = 1L;

        private final long traceId;
        private final long parentId;
        private final long spanId;

        public TraceSpanContext() {
            this.traceId = IdGenerator.generate();
            this.spanId = IdGenerator.generate();
            this.parentId = 0;
        }

        public TraceSpanContext(final long traceId, final long parentId, final long spanId) {
            this.traceId = traceId;
            this.parentId = parentId;
            this.spanId = (spanId != -1L) ? spanId : IdGenerator.generate();
        }

        public long getTraceId() {
            return traceId;
        }

        public long getParentId() {
            return parentId;
        }

        public long getSpanId() {
            return spanId;
        }
    }
}
