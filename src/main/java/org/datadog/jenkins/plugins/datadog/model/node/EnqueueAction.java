package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.util.DatadogActionConverter;

public class EnqueueAction extends QueueInfoAction {

    private static final long serialVersionUID = 1L;

    private final long timestampNanos;

    public EnqueueAction(long timestampNanos) {
        this.timestampNanos = timestampNanos;
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnqueueAction that = (EnqueueAction) o;
        return timestampNanos == that.timestampNanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestampNanos);
    }

    @Override
    public String toString() {
        return "EnqueueAction{timestampNanos=" + timestampNanos + '}';
    }

    public static final class ConverterImpl extends DatadogActionConverter {
        public ConverterImpl(XStream xs) {
        }

        @Override
        public boolean canConvert(Class type) {
            return EnqueueAction.class == type;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            EnqueueAction action = (EnqueueAction) source;
            writeField("timestampNanos", action.timestampNanos, writer, context);
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            long timestampNanos = readField(reader, context, long.class);
            return new EnqueueAction(timestampNanos);
        }
    }
}
