package org.datadog.jenkins.plugins.datadog.util.conversion;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.AbstractReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

/**
 * A subtype of reflection converter that is capable of handling polymorphic fields
 * (when the declared field type is an interface or a parent class and its value is an instance of an implementation or a child class).
 * <p>
 * When marshalling, the converter writes additional {@code resolves-to} attribute that contains the name of the serialized class.
 * When unmarshalling, the {@link AbstractReflectionConverter#instantiateNewInstance(HierarchicalStreamReader, UnmarshallingContext)} method
 * reads the attribute and creates an instance of the correct class.
 */
public class PolymorphicReflectionConverter extends ReflectionConverter {

    public PolymorphicReflectionConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        super(mapper, reflectionProvider);
    }

    @Override
    public void marshal(Object original, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        writer.addAttribute("resolves-to", mapper.serializedClass(original.getClass()));
        super.marshal(original, writer, context);
    }
}