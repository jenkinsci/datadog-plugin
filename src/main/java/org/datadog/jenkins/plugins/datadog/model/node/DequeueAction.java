package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class DequeueAction extends QueueInfoAction {

    private static final long serialVersionUID = 1L;

    private final long queueTimeNanos;

    public DequeueAction(long queueTimeNanos) {
        this.queueTimeNanos = queueTimeNanos;
    }

    public long getQueueTimeNanos() {
        return queueTimeNanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DequeueAction action = (DequeueAction) o;
        return queueTimeNanos == action.queueTimeNanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queueTimeNanos);
    }

    @Override
    public String toString() {
        return "DequeueAction{queueTimeNanos=" + queueTimeNanos + '}';
    }

    public static final class ConverterImpl extends DatadogActionConverter<DequeueAction> {
        public ConverterImpl(XStream xs) {
            super(new ConverterV1());
        }
    }

    public static final class ConverterV1 extends VersionedConverter<DequeueAction> {

        private static final int VERSION = 1;

        public ConverterV1() {
            super(VERSION);
        }

        @Override
        public void marshal(DequeueAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            writeField("queueTimeNanos", action.queueTimeNanos, writer, context);
            context.convertAnother(action.queueTimeNanos);
        }

        @Override
        public DequeueAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            long queueTimeNanos = readField(reader, context, long.class);
            return new DequeueAction(queueTimeNanos);
        }
    }
}
