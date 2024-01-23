package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.datadog.jenkins.plugins.datadog.traces.IdGenerator;
import org.datadog.jenkins.plugins.datadog.util.DatadogActionConverter;

public class TraceInfoAction extends DatadogPluginAction {

    private final ConcurrentMap<String, Long> spanIdByNodeId;

    public TraceInfoAction() {
        this(Collections.emptyMap());
    }

    public TraceInfoAction(Map<String, Long> spanIdByNodeId) {
        this.spanIdByNodeId = new ConcurrentHashMap<>(spanIdByNodeId);
    }

    public Long getOrCreate(String flowNodeId) {
        return spanIdByNodeId.computeIfAbsent(flowNodeId, k -> IdGenerator.generate());
    }

    public Long removeOrCreate(String flowNodeId) {
        Long existingId = spanIdByNodeId.remove(flowNodeId);
        if (existingId != null) {
            return existingId;
        } else {
            return IdGenerator.generate();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraceInfoAction that = (TraceInfoAction) o;
        return Objects.equals(spanIdByNodeId, that.spanIdByNodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spanIdByNodeId);
    }

    @Override
    public String toString() {
        return "TraceInfoAction{" +
                "infoByFlowNodeId=" + spanIdByNodeId +
                '}';
    }

    public static final class ConverterImpl extends DatadogActionConverter {
        public ConverterImpl(XStream xs) {
        }

        @Override
        public boolean canConvert(Class type) {
            return TraceInfoAction.class == type;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            TraceInfoAction action = (TraceInfoAction) source;
            writeField("infoByFlowNodeId", action.spanIdByNodeId, writer, context);
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Map<String, Long> infoByFlowNodeId = readField(reader, context, Map.class);
            return new TraceInfoAction(infoByFlowNodeId);
        }
    }
}
