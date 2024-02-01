package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import org.datadog.jenkins.plugins.datadog.model.ActionConverterTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NodeInfoActionConverterTest extends ActionConverterTest<NodeInfoAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new NodeInfoAction("nodeName", "nodeHostname", Collections.singleton("nodeLabel"), "nodeWorkspace")},
                {new NodeInfoAction("nodeName", "nodeHostname", new HashSet<>(Arrays.asList("nodeLabel1", "nodeLabel2")), null)},
                {new NodeInfoAction(null, "nodeHostname", new HashSet<>(Arrays.asList("nodeLabel1", "nodeLabel2")), "nodeWorkspace")},
                {new NodeInfoAction("nodeName", null, new HashSet<>(Arrays.asList("nodeLabel1", "nodeLabel2")), null)},
                {new NodeInfoAction("nodeName", "nodeHostname", Collections.emptySet(), "nodeWorkspace")},
                {new NodeInfoAction(null, null, new HashSet<>(Arrays.asList("nodeLabel1", "nodeLabel2")), null)},
                {new NodeInfoAction(null, "nodeHostname", Collections.emptySet(), "nodeWorkspace")},
                {new NodeInfoAction("nodeName", null, Collections.emptySet(), null)},
                {new NodeInfoAction(null, null, Collections.emptySet(), null)},
        });
    }

    public NodeInfoActionConverterTest(final NodeInfoAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        return new NodeInfoAction.ConverterImpl(xStream);
    }
}
