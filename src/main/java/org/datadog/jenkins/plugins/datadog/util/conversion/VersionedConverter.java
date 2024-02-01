package org.datadog.jenkins.plugins.datadog.util.conversion;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public abstract class VersionedConverter<T> {
    private final int version;

    protected VersionedConverter(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public abstract void marshal(T source, HierarchicalStreamWriter writer, MarshallingContext context);

    public abstract T unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context);

    protected void writeField(String name, Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
        writer.startNode(name);
        context.convertAnother(value);
        writer.endNode();
    }

    protected <F> F readField(HierarchicalStreamReader reader, UnmarshallingContext context, Class<F> type) {
        reader.moveDown();
        F value = (F) context.convertAnother(null, type);
        reader.moveUp();
        return value;
    }
}
