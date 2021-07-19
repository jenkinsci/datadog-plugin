package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.traces.TraceSpan.TraceSpanContext.PRIORITY_SAMPLING_KEY;

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

        // Avoid sampling
        this.metrics.put(PRIORITY_SAMPLING_KEY, 1.0);
    }

    public TraceSpan(final String name, final long startNs, final TraceSpanContext parent) {
        this.name = name;
        this.startNs = startNs;
        this.traceSpanContext = new TraceSpanContext(parent);
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
        public static final String PRIORITY_SAMPLING_KEY = "_sampling_priority_v1";

        private final long traceId;
        private final long parentId;
        private final long spanId;

        public TraceSpanContext() {
            this.traceId = IdGenerator.generate();
            this.spanId = IdGenerator.generate();
            this.parentId = 0;
        }

        public TraceSpanContext(final TraceSpanContext parent) {
            this.traceId = parent.getTraceId();
            this.parentId = parent.getSpanId();
            this.spanId = IdGenerator.generate();
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
