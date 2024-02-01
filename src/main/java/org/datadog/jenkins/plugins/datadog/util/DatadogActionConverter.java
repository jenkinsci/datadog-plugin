package org.datadog.jenkins.plugins.datadog.util;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public abstract class DatadogActionConverter implements Converter {
    protected void writeField(String name, Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
        writer.startNode(name);
        context.convertAnother(value);
        writer.endNode();
    }

    protected <T> T readField(HierarchicalStreamReader reader, UnmarshallingContext context, Class<T> type) {
        reader.moveDown();
        T value = (T) context.convertAnother(null, type);
        reader.moveUp();
        return value;
    }
}
