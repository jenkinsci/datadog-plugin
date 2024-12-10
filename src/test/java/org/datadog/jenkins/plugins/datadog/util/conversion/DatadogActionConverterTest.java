package org.datadog.jenkins.plugins.datadog.util.conversion;

import static org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter.ignoreOldData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.util.XStream2;
import java.io.StringWriter;
import org.junit.BeforeClass;
import org.junit.Test;

public class DatadogActionConverterTest {

    private static final XStream XSTREAM = new XStream2(XStream2.getDefaultDriver());

    @BeforeClass
    public static void setUp() {
        XSTREAM.registerConverter(new MyConverter());
    }

    @Test
    public void testUsesMostRecentConverterVersionWhenSerializing() {
        StringWriter stringWriter = new StringWriter();
        MyClass myClass = new MyClass();
        XSTREAM.toXML(myClass, stringWriter);
        String serializedMyClass = stringWriter.getBuffer().toString();
        assertTrue(serializedMyClass.contains("v=\"3\"")); // this is the meta attribute set by parent converter
        assertTrue(serializedMyClass.contains("written-with-version-3")); // this is the serialized data written by child converter
    }

    @Test
    public void testUsesAppropriateConverterVersionWhenDeserializing() {
        String serializedWithV3 = "<org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass v=\"3\">written-with-version-3</org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass>";
        MyClass deserializedWithV3 = (MyClass) XSTREAM.fromXML(serializedWithV3);
        assertEquals(3, deserializedWithV3.deserializedWithVersion);

        String serializedWithV2 = "<org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass v=\"2\">written-with-version-2</org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass>";
        MyClass deserializedWithV2 = (MyClass) XSTREAM.fromXML(serializedWithV2);
        assertEquals(2, deserializedWithV2.deserializedWithVersion);

        String serializedWithV1 = "<org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass v=\"1\">written-with-version-1</org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass>";
        MyClass deserializedWithV1 = (MyClass) XSTREAM.fromXML(serializedWithV1);
        assertEquals(1, deserializedWithV1.deserializedWithVersion);
    }

    @Test
    public void testDoesNotDeserializeUnversionedData() {
        String serializedUnversioned = "<org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass>written-without-version</org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass>";
        MyClass deserializedUnversioned = (MyClass) XSTREAM.fromXML(serializedUnversioned);
        assertNull(deserializedUnversioned);
    }

    @Test
    public void testDoesNotDeserializeDataWithUnsupportedVersion() {
        String serializedWithV0 = "<org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass v=\"0\">written-with-version-0</org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass>";
        MyClass deserializedWithV0 = (MyClass) XSTREAM.fromXML(serializedWithV0);
        assertNull(deserializedWithV0);
    }

    @Test
    public void testDoesNotDeserializeDataWithMalformedVersion() {
        String serializedWithCorruptedVersion = "<org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass v=\"abc\">written-with-corrupted-version</org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverterTest_-MyClass>";
        MyClass deserializedWithCorruptedVersion = (MyClass) XSTREAM.fromXML(serializedWithCorruptedVersion);
        assertNull(deserializedWithCorruptedVersion);
    }

    private static final class MyClass {
        private int deserializedWithVersion;
    }

    private static final class MyConverter extends DatadogConverter<MyClass> {
        public MyConverter() {
            super(ignoreOldData(), new MyVersionedConverter(1), new MyVersionedConverter(2), new MyVersionedConverter(3));
        }
    }

    private static final class MyVersionedConverter extends VersionedConverter<MyClass> {
        public MyVersionedConverter(int version) {
            super(version);
        }

        @Override
        public void marshal(MyClass source, HierarchicalStreamWriter writer, MarshallingContext context) {
            writer.setValue("written-with-version-" + getVersion());
        }

        @Override
        public MyClass unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            MyClass myClass = new MyClass();
            myClass.deserializedWithVersion = getVersion();
            return myClass;
        }
    }


}