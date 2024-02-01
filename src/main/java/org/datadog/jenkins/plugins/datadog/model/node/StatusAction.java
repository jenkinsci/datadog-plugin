package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.model.DatadogPluginAction;
import org.datadog.jenkins.plugins.datadog.model.Status;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class StatusAction extends DatadogPluginAction {

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

    public static final class ConverterImpl extends DatadogActionConverter<StatusAction> {
        public ConverterImpl(XStream xs) {
            super(new ConverterV1());
        }
    }

    public static final class ConverterV1 extends VersionedConverter<StatusAction> {
        public ConverterV1() {
            super(1);
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
