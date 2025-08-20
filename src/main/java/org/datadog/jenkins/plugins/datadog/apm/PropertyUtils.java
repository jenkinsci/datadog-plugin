package org.datadog.jenkins.plugins.datadog.apm;

import java.util.Map;

public class PropertyUtils {
    public static String prepend(Map<String, String> envs, String propertyName, String propertyValue) {
        String existingPropertyValue = envs.get(propertyName);
        return propertyValue + (existingPropertyValue != null ? " " + existingPropertyValue : "");
    }

    public static String append(Map<String, String> envs, String propertyName, String propertyValue) {
        String existingPropertyValue = envs.get(propertyName);
        return (existingPropertyValue != null ?  existingPropertyValue + " " : "") + propertyValue;
    }
}
