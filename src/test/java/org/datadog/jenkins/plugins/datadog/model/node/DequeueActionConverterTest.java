package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import org.datadog.jenkins.plugins.datadog.model.ActionConverterTest;
import org.datadog.jenkins.plugins.datadog.model.node.DequeueAction;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DequeueActionConverterTest extends ActionConverterTest<DequeueAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new DequeueAction(0)},
                {new DequeueAction(12345)},
        });
    }

    public DequeueActionConverterTest(final DequeueAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        return new DequeueAction.ConverterImpl(xStream);
    }
}
