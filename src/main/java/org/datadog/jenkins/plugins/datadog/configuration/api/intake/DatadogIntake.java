package org.datadog.jenkins.plugins.datadog.configuration.api.intake;

import hudson.DescriptorExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import jenkins.model.Jenkins;

public abstract class DatadogIntake implements Describable<DatadogIntake>, Serializable {

    public abstract String getApiUrl();
    public abstract String getLogsUrl();
    public abstract String getWebhooksUrl();
    public abstract String getSite();

    public static abstract class DatadogIntakeDescriptor extends Descriptor<DatadogIntake> {
        public static DescriptorExtensionList<DatadogIntake, DatadogIntakeDescriptor> all() {
            return Jenkins.getInstanceOrNull().getDescriptorList(DatadogIntake.class);
        }
    }
}
