package org.datadog.jenkins.plugins.datadog.util.conversion;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

public class PatternListConverter implements Converter {

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        String string = DatadogUtilities.listToCstr((List<Pattern>) source, Pattern::toString);
        context.convertAnother(string);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String string = (String) context.convertAnother(null, String.class);
        if (StringUtils.isBlank(string)) {
            return null;
        }
        return DatadogUtilities.cstrToList(string, Pattern::compile);
    }

    @Override
    public boolean canConvert(Class type) {
        return List.class.isAssignableFrom(type);
    }
}
