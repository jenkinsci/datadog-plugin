package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import org.datadog.jenkins.plugins.datadog.model.ActionConverterTest;
import org.datadog.jenkins.plugins.datadog.model.node.EnqueueAction;
import org.datadog.jenkins.plugins.datadog.model.node.StatusAction;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnqueueActionConverterTest extends ActionConverterTest<EnqueueAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new EnqueueAction(0)},
                {new EnqueueAction(12345)},
        });
    }

    public EnqueueActionConverterTest(final EnqueueAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        return new EnqueueAction.ConverterImpl(xStream);
    }
}
