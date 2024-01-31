package org.datadog.jenkins.plugins.datadog.model.node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import org.datadog.jenkins.plugins.datadog.model.ActionConverterTest;
import org.datadog.jenkins.plugins.datadog.model.Status;
import org.datadog.jenkins.plugins.datadog.model.node.StatusAction;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StatusActionConverterTest extends ActionConverterTest<StatusAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new StatusAction(Status.SUCCESS, true)},
                {new StatusAction(Status.ERROR, false)},
                {new StatusAction(Status.UNKNOWN, true)}
        });
    }

    public StatusActionConverterTest(final StatusAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        return new StatusAction.ConverterImpl(xStream);
    }
}
