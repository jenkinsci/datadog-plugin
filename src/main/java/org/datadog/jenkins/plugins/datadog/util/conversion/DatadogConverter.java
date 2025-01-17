package org.datadog.jenkins.plugins.datadog.util.conversion;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Converter that supports data versioning.
 * Different data version = different serialization format for objects of the given type.
 * Each version is handled by a child {@link VersionedConverter} that encapsulates serialization/deserialization logic for that specific version.
 * <p>
 * Serialized data contains version in an attribute.
 * When data is deserialized, the version is used to determine which child converter should handle deserialization.
 * <p>
 * If you want to change serialization format for a specific type,
 * leave the previous {@link VersionedConverter} instances in place (they will be used to deserialize older data)
 * and add a new one with a higher version number.
 *
 * @param <T> The type of converted objects (should be as narrow as possible, so that different converters do not conflict)
 */
public abstract class DatadogConverter<T> implements Converter {

    private static final String VERSION_ATTRIBUTE = "v";

    private final Class<?> convertedType;
    private final VersionedConverter<T> writeConverter;
    private final Map<Integer, VersionedConverter<T>> readConverters;
    private final VersionedConverter<T> legacyConverter;

    /**
     * @param legacyConverter Converter that is used to unmarshall legacy data written by older plugin versions
     * @param converters The list of versioned converters.
     *                   Writing is always done with the most up-to-date converter (the one with the maximum version).
     *                   Reading is done with the appropriate converter (the one that has version that matches the data version).
     *                   If converter with the specified version does not exist, or data has no version attribute (written by an old version of the plugin),
     *                   no deserialization is performed.
     */
    @SafeVarargs
    protected DatadogConverter(VersionedConverter<T> legacyConverter, @Nonnull VersionedConverter<T>... converters) {
        this.legacyConverter = legacyConverter;

        if (converters.length == 0) {
            throw new IllegalArgumentException("At least one converter is needed");
        }

        readConverters = new HashMap<>();
        for (VersionedConverter<T> converter : converters) {
            VersionedConverter<T> existingConverter = readConverters.put(converter.getVersion(), converter);
            if (existingConverter != null) {
                throw new IllegalArgumentException(String.format("Two converters have the same version: %s (%d), %s (%d)",
                        converter, converter.getVersion(), existingConverter, existingConverter.getVersion()));
            }
        }

        writeConverter = readConverters.values()
                .stream()
                .max(Comparator.comparingInt(VersionedConverter::getVersion))
                // this shouldn't be possible since readConverters must be non-empty
                .orElseThrow(() -> new IllegalArgumentException("Cannot find converter with max version"));

        // only direct children are supported, to keep logic simple
        if (!getClass().getSuperclass().equals(DatadogConverter.class)) {
            throw new IllegalArgumentException(getClass().getName() + " is not a direct descendant of " + DatadogConverter.class.getName());
        }

        ParameterizedType genericSuperclass = (ParameterizedType) getClass().getGenericSuperclass();
        Type[] actualTypeArguments = genericSuperclass.getActualTypeArguments();
        convertedType = (Class<?>) actualTypeArguments[0];
    }

    @Override
    public boolean canConvert(Class type) {
        return type.equals(convertedType);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        writer.addAttribute(VERSION_ATTRIBUTE, String.valueOf(writeConverter.getVersion()));
        writeConverter.marshal((T) source, writer, context);
    }

    @Nullable
    @Override
    public T unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        VersionedConverter<T> readConverter = getReadConverter(reader);
        if (readConverter != null) {
            return readConverter.unmarshal(reader, context);
        } else {
            // no matching converter found, data is too old or corrupted
            return null;
        }
    }

    @Nullable
    private VersionedConverter<T> getReadConverter(HierarchicalStreamReader reader) {
        try {
            String versionString = reader.getAttribute(VERSION_ATTRIBUTE);
            if (versionString == null) {
                // no attribute, data was written by an old version of the plugin
                return legacyConverter;
            }
            int version = Integer.parseInt(versionString);
            return readConverters.get(version);

        } catch (NumberFormatException e) {
            // version is malformed
            return null;
        }
    }
}
