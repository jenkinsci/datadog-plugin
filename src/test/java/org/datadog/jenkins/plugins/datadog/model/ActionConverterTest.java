package org.datadog.jenkins.plugins.datadog.model;

import static org.junit.Assert.assertEquals;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import hudson.util.XStream2;
import org.junit.Before;
import org.junit.Test;

public abstract class ActionConverterTest<T extends DatadogPluginAction>  {

    private final XStream XSTREAM = new XStream2(XStream2.getDefaultDriver());

    private final T action;

    public ActionConverterTest(final T action) {
        this.action = action;
    }

    protected abstract Converter getConverter(XStream xStream);

    @Before
    public void setUp() {
        Converter converter = getConverter(XSTREAM);
        XSTREAM.registerConverter(converter);
        assertEquals(converter, XSTREAM.getConverterLookup().lookupConverterForType(action.getClass()));
    }

    @Test
    public void testStatusActionConverter() {
        String actionXml = XSTREAM.toXML(action);
        T deserializedAction = (T) XSTREAM.fromXML(actionXml);
        assertEquals(action, deserializedAction);
    }

}
