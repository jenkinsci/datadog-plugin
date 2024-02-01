package org.datadog.jenkins.plugins.datadog.traces;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.model.DatadogPluginAction;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

/**
 * Keeps build span propagation
 */
public class BuildSpanAction extends DatadogPluginAction {

    private static final long serialVersionUID = 1L;

    private final TraceSpan.TraceSpanContext buildSpanContext;
    private volatile String buildUrl;

    public BuildSpanAction(final TraceSpan.TraceSpanContext buildSpanContext){
       this.buildSpanContext = buildSpanContext;
    }

    public BuildSpanAction(TraceSpan.TraceSpanContext buildSpanContext, String buildUrl) {
        this.buildSpanContext = buildSpanContext;
        this.buildUrl = buildUrl;
    }

    public TraceSpan.TraceSpanContext getBuildSpanContext() {
        return buildSpanContext;
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public BuildSpanAction setBuildUrl(String buildUrl) {
        this.buildUrl = buildUrl;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildSpanAction that = (BuildSpanAction) o;
        return Objects.equals(buildSpanContext, that.buildSpanContext) && Objects.equals(buildUrl, that.buildUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildSpanContext, buildUrl);
    }

    @Override
    public String toString() {
        return "BuildSpanAction{" +
                "buildSpanContext=" + buildSpanContext +
                ", buildUrl=" + buildUrl +
                '}';
    }

    public static final class ConverterImpl extends DatadogActionConverter<BuildSpanAction> {
        public ConverterImpl(XStream xs) {
            super(new ConverterV1());
        }
    }

    public static final class ConverterV1 extends VersionedConverter<BuildSpanAction> {
        public ConverterV1() {
            super(1);
        }

        @Override
        public void marshal(BuildSpanAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            writeField("spanContext", action.buildSpanContext, writer, context);
            if (action.buildUrl != null) {
                writeField("buildUrl", action.buildUrl, writer, context);
            }
        }

        @Override
        public BuildSpanAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            TraceSpan.TraceSpanContext spanContext = readField(reader, context, TraceSpan.TraceSpanContext.class);

            String buildUrl = null;
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String fieldName = reader.getNodeName();
                if ("buildUrl".equals(fieldName)) {
                    buildUrl = (String) context.convertAnother(null, String.class);
                }
                reader.moveUp();
            }

            return new BuildSpanAction(spanContext, buildUrl);
        }
    }
}
