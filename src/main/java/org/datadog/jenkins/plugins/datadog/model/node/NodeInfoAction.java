package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.datadog.jenkins.plugins.datadog.model.DatadogPluginAction;
import org.datadog.jenkins.plugins.datadog.util.DatadogActionConverter;

public class NodeInfoAction extends DatadogPluginAction {

    private static final long serialVersionUID = 1L;

    private final String nodeName;
    private final String nodeHostname;
    private final Set<String> nodeLabels;
    private final String nodeWorkspace;

    public NodeInfoAction(String nodeName, String nodeHostname, Set<String> nodeLabels, String nodeWorkspace) {
        this.nodeName = nodeName;
        this.nodeHostname = nodeHostname;
        this.nodeLabels = nodeLabels;
        this.nodeWorkspace = nodeWorkspace;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getNodeHostname() {
        return nodeHostname;
    }

    public Set<String> getNodeLabels() {
        return nodeLabels;
    }

    public String getNodeWorkspace() {
        return nodeWorkspace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeInfoAction that = (NodeInfoAction) o;
        return Objects.equals(nodeName, that.nodeName) && Objects.equals(nodeHostname, that.nodeHostname) && Objects.equals(nodeLabels, that.nodeLabels) && Objects.equals(nodeWorkspace, that.nodeWorkspace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeName, nodeHostname, nodeLabels, nodeWorkspace);
    }

    @Override
    public String toString() {
        return "NodeInfoAction{" +
                "nodeName='" + nodeName + '\'' +
                ", nodeHostname='" + nodeHostname + '\'' +
                ", nodeLabels=" + nodeLabels +
                ", nodeWorkspace=" + nodeWorkspace +
                '}';
    }

    public static final class ConverterImpl extends DatadogActionConverter {
        public ConverterImpl(XStream xs) {
        }

        @Override
        public boolean canConvert(Class type) {
            return NodeInfoAction.class == type;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            NodeInfoAction action = (NodeInfoAction) source;
            if (action.nodeName != null) {
                writeField("nodeName", action.nodeName, writer, context);
            }
            if (action.nodeHostname != null) {
                writeField("nodeHostname", action.nodeHostname, writer, context);
            }
            if (action.nodeLabels != null && !action.nodeLabels.isEmpty()) {
                writeField("nodeLabels", action.nodeLabels, writer, context);
            }
            if (action.nodeWorkspace != null && !action.nodeWorkspace.isEmpty()) {
                writeField("nodeWorkspace", action.nodeWorkspace, writer, context);
            }
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            String nodeName = null;
            String nodeHostname = null;
            Set<String> nodeLabels = Collections.emptySet();
            String nodeWorkspace = null;

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
                    case "nodeWorkspace":
                        nodeWorkspace = (String) context.convertAnother(null, String.class);
                        break;
                }
                reader.moveUp();
            }
            return new NodeInfoAction(nodeName, nodeHostname, nodeLabels, nodeWorkspace);
        }
    }

}
