package org.datadog.jenkins.plugins.datadog.configuration;

import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogClient;

public abstract class DatadogClientConfiguration implements Describable<DatadogClientConfiguration>, Serializable {

    public abstract DatadogClient createClient();

    public abstract void validateTracesConnection() throws Descriptor.FormException;

    public abstract void validateLogsConnection() throws Descriptor.FormException;

    public abstract Map<String, String> toEnvironmentVariables();

    public static abstract class DatadogClientConfigurationDescriptor extends Descriptor<DatadogClientConfiguration> {
        public static List<DatadogClientConfigurationDescriptor> all() {
            List<DatadogClientConfigurationDescriptor> descriptors = Jenkins.getInstanceOrNull().getDescriptorList(DatadogClientConfiguration.class);
            List<DatadogClientConfigurationDescriptor> sortedDescriptors = new ArrayList<>(descriptors);
            sortedDescriptors.sort(Comparator.comparingInt(DatadogClientConfigurationDescriptor::getOrder));
            return sortedDescriptors;
        }

        public abstract int getOrder();
    }
}
