package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import org.datadog.jenkins.plugins.datadog.model.ActionConverterTest;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineQueueInfoAction;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PipelineNodeInfoActionConverterTest extends ActionConverterTest<PipelineNodeInfoAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new PipelineNodeInfoAction("nodeName", Collections.singleton("nodeLabels"), "nodeHostname", "workspace")},
                {new PipelineNodeInfoAction("nodeName", new HashSet<>(Arrays.asList("label1", "label2")), "nodeHostname", "workspace")},
                {new PipelineNodeInfoAction(null, Collections.singleton("nodeLabels"), "nodeHostname", "workspace")},
                {new PipelineNodeInfoAction("nodeName", Collections.emptySet(), "nodeHostname", "workspace")},
                {new PipelineNodeInfoAction("nodeName", Collections.singleton("nodeLabels"), null, "workspace")},
                {new PipelineNodeInfoAction("nodeName", Collections.singleton("nodeLabels"), "nodeHostname", null)},
                {new PipelineNodeInfoAction(null, Collections.emptySet(), null, null)},
        });
    }

    public PipelineNodeInfoActionConverterTest(final PipelineNodeInfoAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        return new PipelineNodeInfoAction.ConverterImpl(xStream);
    }
}
