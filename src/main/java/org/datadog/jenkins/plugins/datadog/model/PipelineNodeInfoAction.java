package org.datadog.jenkins.plugins.datadog.model;

import static org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter.ignoreOldData;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class PipelineNodeInfoAction extends DatadogPluginAction {

    private final String nodeName;
    private final Set<String> nodeLabels;
    private final String nodeHostname;
    private final String workspace;

    public PipelineNodeInfoAction(final String nodeName, final Set<String> nodeLabels, final String nodeHostname, String workspace) {
        this.nodeName = nodeName;
        this.nodeLabels = nodeLabels;
        this.nodeHostname = nodeHostname;
        this.workspace = workspace;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Set<String> getNodeLabels() {
        return nodeLabels;
    }

    public String getNodeHostname() {
        return nodeHostname;
    }

    public String getWorkspace() {
        return workspace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineNodeInfoAction that = (PipelineNodeInfoAction) o;
        return Objects.equals(nodeName, that.nodeName) && Objects.equals(nodeLabels, that.nodeLabels) && Objects.equals(nodeHostname, that.nodeHostname) && Objects.equals(workspace, that.workspace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeName, nodeLabels, nodeHostname, workspace);
    }

    @Override
    public String toString() {
        return "PipelineNodeInfoAction{" +
                "nodeName='" + nodeName + '\'' +
                ", nodeLabels=" + nodeLabels +
                ", nodeHostname='" + nodeHostname + '\'' +
                ", workspace='" + workspace + '\'' +
                '}';
    }

    public static final class ConverterImpl extends DatadogConverter<PipelineNodeInfoAction> {
        public ConverterImpl(XStream xs) {
            super(ignoreOldData(), new ConverterV1());
        }
    }

    public static final class ConverterV1 extends VersionedConverter<PipelineNodeInfoAction> {

        private static final int VERSION = 1;

        public ConverterV1() {
            super(VERSION);
        }

        @Override
        public void marshal(PipelineNodeInfoAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            if (action.nodeName != null) {
                writeField("nodeName", action.nodeName, writer, context);
            }
            if (action.nodeHostname != null) {
                writeField("nodeHostname", action.nodeHostname, writer, context);
            }
            if (action.nodeLabels != null && !action.nodeLabels.isEmpty()) {
                writeField("nodeLabels", action.nodeLabels, writer, context);
            }
            if (action.workspace != null) {
                writeField("workspace", action.workspace, writer, context);
            }
        }

        @Override
        public PipelineNodeInfoAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            String nodeName = null;
            String nodeHostname = null;
            Set<String> nodeLabels = Collections.emptySet();
            String workspace = null;

            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String fieldName = reader.getNodeName();
                switch (fieldName) {
                    case "nodeName":
                        nodeName = (String) context.convertAnother(null, String.class);
                        break;
                    case "nodeHostname":
                        nodeHostname = (String) context.convertAnother(null, String.class);
                        break;
                    case "nodeLabels":
                        nodeLabels = (Set) context.convertAnother(null, Set.class);
                        break;
                    case "workspace":
                        workspace = (String) context.convertAnother(null, String.class);
                        break;
                    default:
                        // unknown tag, could be something serialized by a different version of the plugin
                        break;
                }
                reader.moveUp();
            }

            return new PipelineNodeInfoAction(nodeName, nodeLabels, nodeHostname, workspace);
        }
    }
}
