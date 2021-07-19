package org.datadog.jenkins.plugins.datadog.traces;

import java.util.HashMap;
import java.util.Map;

public class TraceSpan {

    private final TraceSpanContext traceSpanContext;
    private final String name;
    private String service;
    private String resource;
    private boolean error;
    private String type;

    private final Map<String, String> meta = new HashMap<>();
    private final Map<String, Double> metrics = new HashMap<>();

    private final long startNs;
    private long endNs;
    private long duration;

    public TraceSpan(final String name, final long startNs) {
        this.name = name;
        this.startNs = startNs;
        this.traceSpanContext = new TraceSpanContext();
    }

    public TraceSpanContext context() {
        return traceSpanContext;
    }

    public void putMeta(String key, String value) {
        this.meta.put(key, value);
    }

    public void putMetric(String key, double value) {
        this.metrics.put(key, value);
    }

    public void setType(final String type) {
        this.type = type;
    }

    public void setResource(final String resource) {
        this.resource = resource;
    }

    public void setService(final String service) {
        this.service = service;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public void setEndNs(final long endNs) {
        this.endNs = endNs;
        this.duration = endNs - startNs;
    }

    public static class TraceSpanContext {
        private final long traceId;
        private final long parentId;
        private final long spanId;

        public TraceSpanContext() {
            this.traceId = IdGenerator.generate();
            this.spanId = IdGenerator.generate();
            this.parentId = 0;
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
