package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TraceInfoActionConverterTest extends ActionConverterTest<TraceInfoAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new TraceInfoAction(Collections.emptyMap())},
                {new TraceInfoAction(Collections.singletonMap("123", 123L))},
                {new TraceInfoAction(new HashMap<>() {{
                    put("123", 123L);
                    put("456", 456L);
                }})},
        });
    }

    public TraceInfoActionConverterTest(final TraceInfoAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        return new TraceInfoAction.ConverterImpl(xStream);
    }
}
