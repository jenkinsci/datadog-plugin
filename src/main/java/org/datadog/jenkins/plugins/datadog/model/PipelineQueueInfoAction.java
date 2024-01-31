package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.util.DatadogActionConverter;

public class PipelineQueueInfoAction extends DatadogPluginAction {

    private static final long serialVersionUID = 1L;

    private volatile long queueTimeMillis = -1;
    private volatile long propagatedQueueTimeMillis = -1;

    public PipelineQueueInfoAction() {}

    public PipelineQueueInfoAction(long queueTimeMillis, long propagatedQueueTimeMillis) {
        this.queueTimeMillis = queueTimeMillis;
        this.propagatedQueueTimeMillis = propagatedQueueTimeMillis;
    }

    public long getQueueTimeMillis() {
        return queueTimeMillis;
    }

    public PipelineQueueInfoAction setQueueTimeMillis(long queueTimeMillis) {
        this.queueTimeMillis = queueTimeMillis;
        return this;
    }

    public long getPropagatedQueueTimeMillis() {
        return propagatedQueueTimeMillis;
    }

    public PipelineQueueInfoAction setPropagatedQueueTimeMillis(long propagatedQueueTimeMillis) {
        this.propagatedQueueTimeMillis = propagatedQueueTimeMillis;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineQueueInfoAction that = (PipelineQueueInfoAction) o;
        return queueTimeMillis == that.queueTimeMillis && propagatedQueueTimeMillis == that.propagatedQueueTimeMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queueTimeMillis, propagatedQueueTimeMillis);
    }

    @Override
    public String toString() {
        return "QueueInfoAction{" +
                "queueTimeMillis=" + queueTimeMillis +
                ", propagatedQueueTimeMillis=" + propagatedQueueTimeMillis +
                '}';
    }

    public static final class ConverterImpl extends DatadogActionConverter {
        public ConverterImpl(XStream xs) {
        }

        @Override
        public boolean canConvert(Class type) {
            return PipelineQueueInfoAction.class == type;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            PipelineQueueInfoAction action = (PipelineQueueInfoAction) source;
            if (action.queueTimeMillis != -1) {
                writeField("queueTimeMillis", action.queueTimeMillis, writer, context);
            }
            if (action.propagatedQueueTimeMillis != -1) {
                writeField("propagatedQueueTimeMillis", action.propagatedQueueTimeMillis, writer, context);
            }
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            long queueTimeMillis = -1;
            long propagatedQueueTimeMillis = -1;

            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String fieldName = reader.getNodeName();
                switch (fieldName) {
                    case "queueTimeMillis":
                        queueTimeMillis = (long) context.convertAnother(null, long.class);
                        break;
                    case "propagatedQueueTimeMillis":
                        propagatedQueueTimeMillis = (long) context.convertAnother(null, long.class);
                        break;
                    default:
                        // unknown tag, could be something serialized by a different version of the plugin
                        break;
                }
                reader.moveUp();
            }

            return new PipelineQueueInfoAction(queueTimeMillis, propagatedQueueTimeMillis);
        }
    }
}
