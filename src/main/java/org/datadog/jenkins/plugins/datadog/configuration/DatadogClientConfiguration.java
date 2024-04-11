package org.datadog.jenkins.plugins.datadog.configuration;

import hudson.DescriptorExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import java.util.Map;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogClient;

public abstract class DatadogClientConfiguration implements Describable<DatadogClientConfiguration>, Serializable {

    public abstract DatadogClient createClient();

    public abstract void validateTracesConnection() throws Descriptor.FormException;

    public abstract void validateLogsConnection() throws Descriptor.FormException;

    public abstract Map<String, String> toEnvironmentVariables();

    public static abstract class DatadogClientConfigurationDescriptor extends Descriptor<DatadogClientConfiguration> {
        public static DescriptorExtensionList<DatadogClientConfiguration, DatadogClientConfigurationDescriptor> all() {
            return Jenkins.getInstanceOrNull().getDescriptorList(DatadogClientConfiguration.class);
        }
    }
}
