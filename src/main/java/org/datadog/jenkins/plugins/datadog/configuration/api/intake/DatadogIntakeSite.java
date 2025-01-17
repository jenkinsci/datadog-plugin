package org.datadog.jenkins.plugins.datadog.configuration.api.intake;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

@Symbol("datadogIntakeSite")
public class DatadogIntakeSite extends DatadogIntake {

    public static final String DATADOG_SITE_PROPERTY = "DATADOG_JENKINS_PLUGIN_DATADOG_SITE";

    static final DatadogSite DEFAULT_DATADOG_SITE_VALUE = DatadogSite.US1;

    private final DatadogSite site;

    @DataBoundConstructor
    public DatadogIntakeSite(DatadogSite site) {
        this.site = site;
    }

    public DatadogSite getSite() {
        return site;
    }

    @Override
    public String getApiUrl() {
        return site.getApiUrl();
    }

    @Override
    public String getLogsUrl() {
        return site.getLogsUrl();
    }

    @Override
    public String getWebhooksUrl() {
        return site.getWebhooksUrl();
    }

    @Override
    public String getSiteName() {
        return site.getSiteName();
    }

    @Override
    public Descriptor<DatadogIntake> getDescriptor() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            throw new RuntimeException("Jenkins instance is null");
        }
        return jenkins.getDescriptorOrDie(DatadogIntakeSite.class);
    }

    @Extension
    public static final class DatadogIntakeSiteDescriptor extends DatadogIntake.DatadogIntakeDescriptor {
        public DatadogIntakeSiteDescriptor() {
            load();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Pick a site";
        }

        @Override
        public String getHelpFile() {
            return getHelpFile("siteBlock");
        }

        @Nullable
        public static DatadogSite getSite() {
            String site = System.getenv().get(DATADOG_SITE_PROPERTY);
            if (site != null) {
                try {
                    return DatadogSite.valueOf(site.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Illegal " + DATADOG_SITE_PROPERTY + " environment property value set: " + site +
                                    ". Allowed values are " + Arrays.toString(DatadogSite.values()), e);
                }
            }
            return null;
        }

        public static DatadogSite getDefaultSite() {
            DatadogSite site = getSite();
            if (site != null) {
                return site;
            } else {
                return DEFAULT_DATADOG_SITE_VALUE;
            }
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DatadogIntakeSite that = (DatadogIntakeSite) o;
        return site == that.site;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(site);
    }
}
