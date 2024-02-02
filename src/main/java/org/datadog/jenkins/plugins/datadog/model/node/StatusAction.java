package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.model.DatadogPluginAction;
import org.datadog.jenkins.plugins.datadog.model.Status;
import org.datadog.jenkins.plugins.datadog.util.DatadogActionConverter;

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

    public static final class ConverterImpl extends DatadogActionConverter {
        public ConverterImpl(XStream xs) {
        }

        @Override
        public boolean canConvert(Class type) {
            return StatusAction.class == type;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            StatusAction action = (StatusAction) source;
            writeField("status", action.status, writer, context);
            writeField("propagate", action.propagate, writer, context);
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Status status = readField(reader, context, Status.class);
            boolean propagate = readField(reader, context, boolean.class);
            return new StatusAction(status, propagate);
        }
    }
}
