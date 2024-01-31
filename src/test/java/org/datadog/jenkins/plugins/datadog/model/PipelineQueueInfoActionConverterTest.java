package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import org.datadog.jenkins.plugins.datadog.model.ActionConverterTest;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineQueueInfoAction;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PipelineQueueInfoActionConverterTest extends ActionConverterTest<PipelineQueueInfoAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new PipelineQueueInfoAction(123, 456)},
                {new PipelineQueueInfoAction(-1, 456)},
                {new PipelineQueueInfoAction(123, -1)},
                {new PipelineQueueInfoAction(-1, -1)},
        });
    }

    public PipelineQueueInfoActionConverterTest(final PipelineQueueInfoAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        return new PipelineQueueInfoAction.ConverterImpl(xStream);
    }
}
