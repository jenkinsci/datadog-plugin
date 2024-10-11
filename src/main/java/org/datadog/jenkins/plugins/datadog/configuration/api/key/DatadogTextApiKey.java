package org.datadog.jenkins.plugins.datadog.configuration.api.key;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntake;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeSite;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogSite;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.util.Objects;

@Symbol("datadogTextApiKey")
public class DatadogTextApiKey extends DatadogApiKey {

    public static final String TARGET_API_KEY_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_KEY";

    private final Secret key;

    @DataBoundConstructor
    public DatadogTextApiKey(Secret key) {
        this.key = key;
    }

    @Override
    public Secret getKey() {
        return key;
    }

    @Override
    public Descriptor<DatadogApiKey> getDescriptor() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            throw new RuntimeException("Jenkins instance is null");
        }
        return jenkins.getDescriptorOrDie(DatadogTextApiKey.class);
    }

    @Extension
    public static final class DatadogTextApiKeyDescriptor extends DatadogApiKeyDescriptor {
        public DatadogTextApiKeyDescriptor() {
            load();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Enter manually";
        }

        @Override
        public String getHelpFile() {
            return getHelpFile("textKeyBlock");
        }

        @RequirePOST
        public FormValidation doCheckKey(@QueryParameter("key") final String key) {
            if (StringUtils.isBlank(key)) {
                return FormValidation.error("Please enter API key");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doCheckConnectivity(@QueryParameter("key") final String key,
                                                  @QueryParameter("intake") final int intakeIdx,
                                                  @QueryParameter("site") final String site,
                                                  @QueryParameter("apiUrl") final String apiUrl) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            Secret apiKeyValue = Secret.fromString(key);
            return checkConnectivity(apiKeyValue, intakeIdx, site, apiUrl);
        }

        public static Secret getDefaultKey() {
            String apiKeyPropertyValue = System.getenv().get(TARGET_API_KEY_PROPERTY);
            return StringUtils.isNotBlank(apiKeyPropertyValue) ? Secret.fromString(apiKeyPropertyValue) : null;
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
        DatadogTextApiKey that = (DatadogTextApiKey) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key);
    }
}
