package org.datadog.jenkins.plugins.datadog.configuration.api.intake;

import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeSite.DatadogIntakeSiteDescriptor.getDefaultSite;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.DatadogIntakeUrlsDescriptor.getDefaultApiUrl;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.DatadogIntakeUrlsDescriptor.getDefaultLogsUrl;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.DatadogIntakeUrlsDescriptor.getDefaultWebhooksUrl;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.TARGET_API_URL_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.TARGET_LOG_INTAKE_URL_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.TARGET_WEBHOOK_INTAKE_URL_PROPERTY;

import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;

public abstract class DatadogIntake implements Describable<DatadogIntake>, Serializable {

    public abstract String getApiUrl();
    public abstract String getLogsUrl();
    public abstract String getWebhooksUrl();
    public abstract String getSiteName();

    public static abstract class DatadogIntakeDescriptor extends Descriptor<DatadogIntake> {
        public static List<DatadogIntakeDescriptor> all() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                throw new RuntimeException("Jenkins instance is null");
            }
            List<DatadogIntakeDescriptor> descriptors = jenkins.getDescriptorList(DatadogIntake.class);
            List<DatadogIntakeDescriptor> sortedDescriptors = new ArrayList<>(descriptors);
            sortedDescriptors.sort(Comparator.comparingInt(DatadogIntakeDescriptor::getOrder));
            return sortedDescriptors;
        }

        public abstract int getOrder();
    }

    public static DatadogIntake getDefaultIntake() {
        Map<String, String> env = System.getenv();
        if (env.containsKey(TARGET_API_URL_PROPERTY)
                || env.containsKey(TARGET_LOG_INTAKE_URL_PROPERTY)
                || env.containsKey(TARGET_WEBHOOK_INTAKE_URL_PROPERTY)) {
            // default to URLs intake if any of the URLs are set with environment variables
            return new DatadogIntakeUrls(getDefaultApiUrl(), getDefaultLogsUrl(), getDefaultWebhooksUrl());
        } else {
            return new DatadogIntakeSite(getDefaultSite());
        }
    }
}
