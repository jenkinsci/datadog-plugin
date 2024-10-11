package org.datadog.jenkins.plugins.datadog.traces.message;

import static org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter.ignoreOldData;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.traces.IdGenerator;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class TraceSpan {

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

    public TraceSpan(final String name, final long startNano, final TraceSpanContext spanContext) {
        if (spanContext == null) {
            throw new NullPointerException("span context cannot be null");
        }

        this.operationName = name;
        this.startNano = startNano;
        this.traceSpanContext = spanContext;

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
    public String toString() {
        return "TraceSpan{" +
                "operationName='" + operationName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", resourceName='" + resourceName + '\'' +
                '}';
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TraceSpanContext that = (TraceSpanContext) o;
            return traceId == that.traceId && parentId == that.parentId && spanId == that.spanId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(traceId, parentId, spanId);
        }

        @Override
        public String toString() {
            return "TraceSpanContext{" +
                    "traceId=" + traceId +
                    ", parentId=" + parentId +
                    ", spanId=" + spanId +
                    '}';
        }

        public static final class ConverterImpl extends DatadogConverter<TraceSpanContext> {
            public ConverterImpl(XStream xs) {
                super(ignoreOldData(), new ConverterV1());
            }
        }

        public static final class ConverterV1 extends VersionedConverter<TraceSpanContext> {

            private static final int VERSION = 1;

            public ConverterV1() {
                super(VERSION);
            }

            @Override
            public void marshal(TraceSpanContext traceSpanContext, HierarchicalStreamWriter writer, MarshallingContext context) {
                writeField("traceId", traceSpanContext.traceId, writer, context);
                writeField("parentId", traceSpanContext.parentId, writer, context);
                writeField("spanId", traceSpanContext.spanId, writer, context);
            }

            @Override
            public TraceSpanContext unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                long traceId = readField(reader, context, long.class);
                long parentId = readField(reader, context, long.class);
                long spanId = readField(reader, context, long.class);
                return new TraceSpanContext(traceId, parentId, spanId);
            }
        }
    }
}
