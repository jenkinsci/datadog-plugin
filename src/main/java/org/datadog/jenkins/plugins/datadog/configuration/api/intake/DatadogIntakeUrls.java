package org.datadog.jenkins.plugins.datadog.configuration.api.intake;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Symbol("datadogIntakeUrls")
public class DatadogIntakeUrls extends DatadogIntake {

    static final String TARGET_API_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_URL";
    static final String TARGET_LOG_INTAKE_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_LOG_INTAKE_URL";
    static final String TARGET_WEBHOOK_INTAKE_URL_PROPERTY = "DATADOG_JENKINS_TARGET_WEBHOOK_INTAKE_URL";

    static final String DEFAULT_API_URL_VALUE = "https://api.datadoghq.com/api/";
    static final String DEFAULT_LOG_INTAKE_URL_VALUE = "https://http-intake.logs.datadoghq.com/v1/input/";
    static final String DEFAULT_WEBHOOK_INTAKE_URL_VALUE = "https://webhook-intake.datadoghq.com/api/v2/webhook/";

    private final String apiUrl;
    private final String logsUrl;
    private final String webhooksUrl;

    @DataBoundConstructor
    public DatadogIntakeUrls(String apiUrl, String logsUrl, String webhooksUrl) {
        this.apiUrl = apiUrl;
        this.logsUrl = logsUrl;
        this.webhooksUrl = webhooksUrl;
    }

    @Override
    public String getApiUrl() {
        return apiUrl;
    }

    @Override
    public String getLogsUrl() {
        return logsUrl;
    }

    @Override
    public String getWebhooksUrl() {
        return webhooksUrl;
    }

    @Override
    public String getSiteName() {
        // what users configure for Pipelines looks like "https://api.datadoghq.com/api/"
        // while what the tracer needs "datadoghq.com"
        try {
            URI uri = new URL(apiUrl).toURI();
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("Cannot find host in Datadog API URL: " + uri);
            }

            String[] parts = host.split("\\.");
            return (parts.length >= 2 ? parts[parts.length - 2] + "." : "") + parts[parts.length - 1];

        } catch (MalformedURLException | URISyntaxException e) {
            throw new IllegalArgumentException("Cannot parse Datadog API URL", e);
        }
    }

    @Override
    public Descriptor<DatadogIntake> getDescriptor() {
        return Jenkins.getInstanceOrNull().getDescriptorOrDie(DatadogIntakeUrls.class);
    }

    @Extension
    public static final class DatadogIntakeUrlsDescriptor extends DatadogIntakeDescriptor {
        public DatadogIntakeUrlsDescriptor() {
            load();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Enter URLs manually";
        }

        @RequirePOST
        public FormValidation doCheckApiUrl(@QueryParameter("apiUrl") final String apiUrl) {
            if (StringUtils.isBlank(apiUrl)) {
                return FormValidation.error("Please enter the API URL");
            }
            return validateUrl(apiUrl);
        }

        @RequirePOST
        public FormValidation doCheckLogsUrl(@QueryParameter("logsUrl") final String logsUrl,
                                             @RelativePath("../..") // parent's parent (this -> API client configuration -> global configuration
                                             @QueryParameter("collectBuildLogs") final boolean collectBuildLogs) {
            if (collectBuildLogs && StringUtils.isBlank(logsUrl)) {
                return FormValidation.error("Log collection is enabled, please enter log intake URL");
            }
            return validateUrl(logsUrl);
        }

        @RequirePOST
        public FormValidation doCheckWebhooksUrl(@QueryParameter("webhooksUrl") final String webhooksUrl,
                                                      @RelativePath("../..") // parent's parent (this -> API client configuration -> global configuration
                                                      @QueryParameter("ciVisibilityData") final boolean enableCiVisibility) {
            if (enableCiVisibility && StringUtils.isBlank(webhooksUrl)) {
                return FormValidation.error("CI Visibility is enabled, please enter webhook intake URL");
            }
            return validateUrl(webhooksUrl);
        }

        private static FormValidation validateUrl(String urlString) {
            if (!StringUtils.isBlank(urlString)) {
                try {
                    URL url = new URL(urlString);
                    if (!url.getProtocol().contains("http")) {
                        return FormValidation.error("The URL has to use either HTTP or HTTPS protocol");
                    }
                } catch (MalformedURLException e) {
                    return FormValidation.error("Please enter a valid URL: " + e.getMessage());
                }
            }
            return FormValidation.ok();
        }

        public static String getDefaultApiUrl() {
            return System.getenv().getOrDefault(TARGET_API_URL_PROPERTY, DEFAULT_API_URL_VALUE);
        }

        public static String getDefaultLogsUrl() {
            return System.getenv().getOrDefault(TARGET_LOG_INTAKE_URL_PROPERTY, DEFAULT_LOG_INTAKE_URL_VALUE);
        }

        public static String getDefaultWebhooksUrl() {
            return System.getenv().getOrDefault(TARGET_WEBHOOK_INTAKE_URL_PROPERTY, DEFAULT_WEBHOOK_INTAKE_URL_VALUE);
        }

        @Override
        public int getOrder() {
            return 1;
        }
    }
}
