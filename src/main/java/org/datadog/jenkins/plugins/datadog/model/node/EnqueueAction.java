package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class EnqueueAction extends QueueInfoAction {

    private static final long serialVersionUID = 1L;

    private final long timestampMillis;

    public EnqueueAction(long timestampMillis) {
        this.timestampMillis = timestampMillis;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnqueueAction that = (EnqueueAction) o;
        return timestampMillis == that.timestampMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestampMillis);
    }

    @Override
    public String toString() {
        return "EnqueueAction{timestampMillis=" + timestampMillis + '}';
    }

    public static final class ConverterImpl extends DatadogActionConverter<EnqueueAction> {
        public ConverterImpl(XStream xs) {
            super(new ConverterV1(), new ConverterV2());
        }
    }

    public static final class ConverterV1 extends VersionedConverter<EnqueueAction> {
        private static final int VERSION = 1;

        public ConverterV1() {
            super(VERSION);
        }

        @Override
        public void marshal(EnqueueAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            writeField("timestampNanos", TimeUnit.MILLISECONDS.toNanos(action.timestampMillis), writer, context);
        }

        @Override
        public EnqueueAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            long timestampNanos = readField(reader, context, long.class);
            return new EnqueueAction(TimeUnit.NANOSECONDS.toMillis(timestampNanos));
        }
    }

    public static final class ConverterV2 extends VersionedConverter<EnqueueAction> {
        private static final int VERSION = 2;

        public ConverterV2() {
            super(VERSION);
        }

        @Override
        public void marshal(EnqueueAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            writeField("timestampMillis", action.timestampMillis, writer, context);
        }

        @Override
        public EnqueueAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            long timestampMillis = readField(reader, context, long.class);
            return new EnqueueAction(timestampMillis);
        }
    }
}
