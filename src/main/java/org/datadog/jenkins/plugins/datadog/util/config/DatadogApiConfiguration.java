package org.datadog.jenkins.plugins.datadog.util.config;

import static org.datadog.jenkins.plugins.datadog.util.config.DatadogTextApiKey.DatadogTextApiKeyDescriptor.getDefaultKey;

import com.thoughtworks.xstream.annotations.XStreamConverter;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogApiClient;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;
import org.datadog.jenkins.plugins.datadog.util.conversion.PolymorphicReflectionConverter;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Symbol("datadogApiConfiguration")
public class DatadogApiConfiguration extends DatadogClientConfiguration {

    static final String TARGET_API_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_URL";
    static final String TARGET_LOG_INTAKE_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_LOG_INTAKE_URL";
    static final String TARGET_WEBHOOK_INTAKE_URL_PROPERTY = "DATADOG_JENKINS_TARGET_WEBHOOK_INTAKE_URL";
    static final String TARGET_API_KEY_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_KEY";

    static final String DEFAULT_API_URL_VALUE = "https://api.datadoghq.com/api/";
    static final String DEFAULT_LOG_INTAKE_URL_VALUE = "https://http-intake.logs.datadoghq.com/v1/input/";
    static final String DEFAULT_WEBHOOK_INTAKE_URL_VALUE = "https://webhook-intake.datadoghq.com/api/v2/webhook/";

    private final String apiUrl;
    private final String logIntakeUrl;
    private final String webhookIntakeUrl;
    @XStreamConverter(PolymorphicReflectionConverter.class)
    private final DatadogApiKey apiKey;

    @DataBoundConstructor
    public DatadogApiConfiguration(String apiUrl, String logIntakeUrl, String webhookIntakeUrl, DatadogApiKey apiKey) {
        this.apiUrl = apiUrl;
        this.logIntakeUrl = logIntakeUrl;
        this.webhookIntakeUrl = webhookIntakeUrl;
        this.apiKey = apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getLogIntakeUrl() {
        return logIntakeUrl;
    }

    public String getWebhookIntakeUrl() {
        return webhookIntakeUrl;
    }

    public DatadogApiKey getApiKey() {
        return apiKey;
    }

    private Secret getApiKeyValue() {
        return apiKey != null ? apiKey.getKey() : null;
    }

    @Override
    public DatadogClient createClient() {
        return new DatadogApiClient(apiUrl, logIntakeUrl, webhookIntakeUrl, getApiKeyValue());
    }

    @Override
    public void validateTracesConnection() throws Descriptor.FormException {
        if (StringUtils.isBlank(webhookIntakeUrl)) {
            throw new Descriptor.FormException("CI Visibility requires webhook intake URL to be set", "webhookIntakeUrl");
        }
        checkConnectivity("{}", webhookIntakeUrl, JSONSerializer::toJSON, "webhookIntakeUrl");
    }

    @Override
    public void validateLogsConnection() throws Descriptor.FormException {
        if (StringUtils.isBlank(logIntakeUrl)) {
            throw new Descriptor.FormException("Logs collection requires log intake URL to be set", "logIntakeUrl");
        }
        String payload = "{\"message\":\"[datadog-plugin] Check connection\", " +
                "\"ddsource\":\"Jenkins\", \"service\":\"Jenkins\", " +
                "\"hostname\":\"" + DatadogUtilities.getHostname(null) + "\"}";
        checkConnectivity(payload, logIntakeUrl, Function.identity(), "logIntakeUrl");
    }

    private void checkConnectivity(String payload, String url, Function<String, ?> responseParser, String field) throws Descriptor.FormException {
        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(getApiKeyValue()));

        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        try {
            new HttpClient(30_000).post(url, headers, "application/json", body, responseParser);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Descriptor.FormException("Interrupted while trying to check logs intake connectivity", field);

        } catch (Exception e) {
            throw new Descriptor.FormException("Datadog connectivity check failed: " + e.getMessage(), field);
        }
    }

    @Override
    public Map<String, String> toEnvironmentVariables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("DD_CIVISIBILITY_AGENTLESS_ENABLED", "true");
        variables.put("DD_SITE", getSite(apiUrl));
        variables.put("DD_API_KEY", Secret.toString(getApiKeyValue()));
        return variables;
    }

    private static String getSite(String apiUrl) {
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
    public Descriptor<DatadogClientConfiguration> getDescriptor() {
        return Jenkins.getInstanceOrNull().getDescriptorOrDie(DatadogApiConfiguration.class);
    }

    @Extension
    public static final class DatadogApiConfigurationDescriptor extends DatadogClientConfigurationDescriptor {
        public DatadogApiConfigurationDescriptor() {
            load();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Use Datadog site and API key to report to Datadog";
        }

        @RequirePOST
        public FormValidation doCheckApiUrl(@QueryParameter("apiUrl") final String apiUrl) {
            if (StringUtils.isBlank(apiUrl)) {
                return FormValidation.error("Please enter the API URL");
            }
            return validateUrl(apiUrl);
        }

        @RequirePOST
        public FormValidation doCheckLogIntakeUrl(@QueryParameter("logIntakeUrl") final String logIntakeUrl,
                                                  @RelativePath("..")
                                                  @QueryParameter("collectBuildLogs") final boolean collectBuildLogs) {
            if (collectBuildLogs && StringUtils.isBlank(logIntakeUrl)) {
                return FormValidation.error("Log collection is enabled, please enter log intake URL");
            }
            return validateUrl(logIntakeUrl);
        }

        @RequirePOST
        public FormValidation doCheckWebhookIntakeUrl(@QueryParameter("webhookIntakeUrl") final String webhookIntakeUrl,
                                                      @RelativePath("..")
                                                      @QueryParameter("ciVisibilityData") final boolean enableCiVisibility) {
            if (enableCiVisibility && StringUtils.isBlank(webhookIntakeUrl)) {
                return FormValidation.error("CI Visibility is enabled, please enter webhook intake URL");
            }
            return validateUrl(webhookIntakeUrl);
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

        public static String getDefaultLogIntakeUrl() {
            return System.getenv().getOrDefault(TARGET_LOG_INTAKE_URL_PROPERTY, DEFAULT_LOG_INTAKE_URL_VALUE);
        }

        public static String getDefaultWebhookIntakeUrl() {
            return System.getenv().getOrDefault(TARGET_WEBHOOK_INTAKE_URL_PROPERTY, DEFAULT_WEBHOOK_INTAKE_URL_VALUE);
        }

        public static DatadogApiKey getDefaultApiKey() {
            return new DatadogTextApiKey(getDefaultKey());
        }

        public DescriptorExtensionList<DatadogApiKey, DatadogApiKey.DatadogApiKeyDescriptor> getApiKeyOptions() {
            return DatadogApiKey.DatadogApiKeyDescriptor.all();
        }
    }
}
