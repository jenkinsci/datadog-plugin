package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BuildSpanActionConverterTest extends ActionConverterTest<BuildSpanAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new BuildSpanAction(new TraceSpan.TraceSpanContext(123, 456, 789), 0, "buildUrl")},
                {new BuildSpanAction(new TraceSpan.TraceSpanContext(0, 456, 789), 1, "buildUrl")},
                {new BuildSpanAction(new TraceSpan.TraceSpanContext(123, 0, 789), 2, null)},
                {new BuildSpanAction(new TraceSpan.TraceSpanContext(123, 456, 0), 3, "buildUrl")},
                {new BuildSpanAction(new TraceSpan.TraceSpanContext(0, 0, 0), 4, null)},
        });
    }

    public BuildSpanActionConverterTest(final BuildSpanAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        xStream.registerConverter(new TraceSpan.TraceSpanContext.ConverterImpl(xStream));
        return new BuildSpanAction.ConverterImpl(xStream);
    }
}
