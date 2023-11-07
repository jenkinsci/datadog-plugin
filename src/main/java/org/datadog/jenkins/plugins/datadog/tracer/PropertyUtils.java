package org.datadog.jenkins.plugins.datadog.tracer;

import java.util.Map;

public class PropertyUtils {
    public static String prepend(Map<String, String> envs, String propertyName, String propertyValue) {
        String existingPropertyValue = envs.get(propertyName);
        return propertyValue + (existingPropertyValue != null ? " " + existingPropertyValue : "");
    }
}
