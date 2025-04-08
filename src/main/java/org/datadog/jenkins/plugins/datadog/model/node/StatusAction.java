package org.datadog.jenkins.plugins.datadog.model.node;

import static org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter.ignoreOldData;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import java.io.Serial;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.model.DatadogPluginAction;
import org.datadog.jenkins.plugins.datadog.model.Status;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class StatusAction extends DatadogPluginAction {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Status status;
    private final boolean propagate;

    public StatusAction(Status status, boolean propagate) {
        this.status = status;
        this.propagate = propagate;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isPropagate() {
        return propagate;
    }

    @Override
    public String toString() {
        return "StatusAction{" +
                "status='" + status + '\'' +
                ", propagate=" + propagate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatusAction that = (StatusAction) o;
        return propagate == that.propagate && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, propagate);
    }

    public static final class ConverterImpl extends DatadogConverter<StatusAction> {
        public ConverterImpl(XStream xs) {
            super(ignoreOldData(), new ConverterV1());
        }
    }

    public static final class ConverterV1 extends VersionedConverter<StatusAction> {

        private static final int VERSION = 1;

        public ConverterV1() {
            super(VERSION);
        }

        @Override
        public void marshal(StatusAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            writeField("status", action.status, writer, context);
            writeField("propagate", action.propagate, writer, context);
        }

        @Override
        public StatusAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Status status = readField(reader, context, Status.class);
            boolean propagate = readField(reader, context, boolean.class);
            return new StatusAction(status, propagate);
        }
    }
}
