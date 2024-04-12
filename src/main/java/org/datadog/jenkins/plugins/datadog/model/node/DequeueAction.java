package org.datadog.jenkins.plugins.datadog.model.node;

import static org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter.ignoreOldData;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class DequeueAction extends QueueInfoAction {

    private static final long serialVersionUID = 1L;

    private final long queueTimeMillis;

    public DequeueAction(long queueTimeMillis) {
        this.queueTimeMillis = queueTimeMillis;
    }

    public long getQueueTimeMillis() {
        return queueTimeMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DequeueAction action = (DequeueAction) o;
        return queueTimeMillis == action.queueTimeMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queueTimeMillis);
    }

    @Override
    public String toString() {
        return "DequeueAction{queueTimeMillis=" + queueTimeMillis + '}';
    }

    public static final class ConverterImpl extends DatadogConverter<DequeueAction> {
        public ConverterImpl(XStream xs) {
            super(ignoreOldData(), new ConverterV1(), new ConverterV2());
        }
    }

    public static final class ConverterV1 extends VersionedConverter<DequeueAction> {
        private static final int VERSION = 1;

        public ConverterV1() {
            super(VERSION);
        }

        @Override
        public void marshal(DequeueAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            writeField("queueTimeNanos", TimeUnit.MILLISECONDS.toNanos(action.queueTimeMillis), writer, context);
        }

        @Override
        public DequeueAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            long queueTimeNanos = readField(reader, context, long.class);
            return new DequeueAction(TimeUnit.NANOSECONDS.toMillis(queueTimeNanos));
        }
    }

    public static final class ConverterV2 extends VersionedConverter<DequeueAction> {
        private static final int VERSION = 2;

        public ConverterV2() {
            super(VERSION);
        }

        @Override
        public void marshal(DequeueAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            writeField("queueTimeMillis", action.queueTimeMillis, writer, context);
        }

        @Override
        public DequeueAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            long queueTimeMillis = readField(reader, context, long.class);
            return new DequeueAction(queueTimeMillis);
        }
    }
}
