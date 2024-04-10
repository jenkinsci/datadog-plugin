package org.datadog.jenkins.plugins.datadog.util.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogApiClient;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Symbol("datadogApiConfiguration")
public class DatadogApiConfiguration extends DatadogClientConfiguration {

    private static final Logger logger = Logger.getLogger(DatadogApiConfiguration.class.getName());

    static final String TARGET_API_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_URL";
    static final String TARGET_LOG_INTAKE_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_LOG_INTAKE_URL";
    static final String TARGET_WEBHOOK_INTAKE_URL_PROPERTY = "DATADOG_JENKINS_TARGET_WEBHOOK_INTAKE_URL";
    static final String TARGET_API_KEY_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_KEY";

    static final String DEFAULT_API_URL_VALUE = "https://api.datadoghq.com/api/";
    static final String DEFAULT_LOG_INTAKE_URL_VALUE = "https://http-intake.logs.datadoghq.com/v1/input/";
    static final String DEFAULT_WEBHOOK_INTAKE_URL_VALUE = "https://webhook-intake.datadoghq.com/api/v2/webhook/";

    private static final String VALIDATE_ENDPOINT = "v1/validate";

    private final String apiUrl;
    private final String logIntakeUrl;
    private final String webhookIntakeUrl;
    private final Secret apiKey;
    private final String apiKeyCredentialsId;

    @DataBoundConstructor
    public DatadogApiConfiguration(String apiUrl, String logIntakeUrl, String webhookIntakeUrl, Secret apiKey, String apiKeyCredentialsId) {
        this.apiUrl = apiUrl;
        this.logIntakeUrl = logIntakeUrl;
        this.webhookIntakeUrl = webhookIntakeUrl;
        this.apiKey = apiKey;
        this.apiKeyCredentialsId = apiKeyCredentialsId;
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

    public Secret getApiKey() {
        return apiKey;
    }

    public String getApiKeyCredentialsId() {
        return apiKeyCredentialsId;
    }

    @Override
    public DatadogClient createClient() {
        final Secret usedApiKey = getUsedApiKey();
        return new DatadogApiClient(apiUrl, logIntakeUrl, webhookIntakeUrl, usedApiKey);
    }

    /**
     * Gets effective API key
     * (API key selected from credentials takes precedence over API key entered manually).
     */
    private Secret getUsedApiKey() {
        return getCredential(apiKeyCredentialsId, apiKey);
    }

    /**
     * Gets the secret with the specified credential ID.
     * If the secret cannot be retrieved, the fallback value is used.
     */
    static Secret getCredential(String credentialsId, Secret fallback) {
        if (credentialsId != null && !StringUtils.isBlank(credentialsId)) {
            StringCredentials credential = getCredentialFromId(credentialsId);
            if (credential != null && !credential.getSecret().getPlainText().isEmpty()) {
                return credential.getSecret();
            }
        }
        return fallback;
    }

    /**
     * Gets the StringCredentials object for the given credential ID
     *
     * @param credentialId - The ID of the credential to get
     * @return a StringCredentials object
     */
    static StringCredentials getCredentialFromId(String credentialId) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StringCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        URIRequirementBuilder.fromUri(null).build()),
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialId))
        );
    }

    @Override
    public void validateTracesConnection() throws Descriptor.FormException {
        if (StringUtils.isBlank(webhookIntakeUrl)) {
            throw new Descriptor.FormException("CI Visibility requires webhook intake URL to be set", "webhookIntakeUrl");
        }
        checkConnectivity("{}", webhookIntakeUrl, JSONSerializer::toJSON);
    }

    @Override
    public void validateLogsConnection() throws Descriptor.FormException {
        if (StringUtils.isBlank(logIntakeUrl)) {
            throw new Descriptor.FormException("Logs collection requires log intake URL to be set", "logIntakeUrl");
        }
        String payload = "{\"message\":\"[datadog-plugin] Check connection\", " +
                "\"ddsource\":\"Jenkins\", \"service\":\"Jenkins\", " +
                "\"hostname\":\"" + DatadogUtilities.getHostname(null) + "\"}";
        checkConnectivity(payload, logIntakeUrl, Function.identity());
    }

    private void checkConnectivity(String url, String payload, Function<String, ?> responseParser) throws Descriptor.FormException {
        Secret usedApiKey = getUsedApiKey();

        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(usedApiKey));

        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        try {
            new HttpClient(30_000).post(url, headers, "application/json", body, responseParser);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Descriptor.FormException("Interrupted while trying to check logs intake connectivity", "logIntakeUrl");

        } catch (Exception e) {
            throw new Descriptor.FormException("Logs intake connectivity check failed: " + e.getMessage(), "logIntakeUrl");
        }
    }

    @Override
    public Map<String, String> toEnvironmentVariables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("DD_CIVISIBILITY_AGENTLESS_ENABLED", "true");
        variables.put("DD_SITE", getSite(apiUrl));
        variables.put("DD_API_KEY", Secret.toString(getUsedApiKey()));
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
            return "Use Datadog API URL and Key to report to Datadog";
        }

        /**
         * Populates the API key credentials ID dropdown in the configuration screen with all the valid credentials
         *
         * @param item - The context within which to list available credentials
         * @param apiKeyCredentialsId - ID of the credential containing the API key
         * @return a ListBoxModel object used to display all the available credentials.
         */
        public ListBoxModel doFillApiKeyCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter("apiKeyCredentialsId") String apiKeyCredentialsId
        ) {
            StandardListBoxModel result = new StandardListBoxModel();
            // If the user does not have permissions to list credentials, only list the current value
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(apiKeyCredentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(apiKeyCredentialsId);
                }
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM,
                            Jenkins.get(),
                            StringCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(StringCredentials.class))
                    .includeCurrentValue(apiKeyCredentialsId);
        }

        @RequirePOST
        public FormValidation doCheckApiUrl(@QueryParameter("apiUrl") final String apiUrl) {
            return validateUrl(apiUrl);
        }

        @RequirePOST
        public FormValidation doCheckLogIntakeUrl(@QueryParameter("logIntakeUrl") final String logIntakeUrl) {
            return validateUrl(logIntakeUrl);
        }

        @RequirePOST
        public FormValidation doCheckWebhookIntakeUrl(@QueryParameter("webhookIntakeUrl") final String webhookIntakeUrl) {
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

        @RequirePOST
        public FormValidation doCheckConnectivity(@QueryParameter("apiKey") final String apiKey,
                                                  @QueryParameter("apiKeyCredentialsId") final String apiKeyCredentialsId,
                                                  @QueryParameter("apiUrl") final String apiUrl) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            final Secret usedApiKey = getCredential(apiKeyCredentialsId, Secret.fromString(apiKey));
            if (validateApiConnection(apiUrl, usedApiKey)) {
                return FormValidation.ok("Great! Your API key is valid.");
            } else {
                return FormValidation.error("Hmmm, your API key seems to be invalid.");
            }
        }

        private static boolean validateApiConnection(String apiUrl, Secret usedApiKey) {
            String urlParameters = "?api_key=" + Secret.toString(usedApiKey);
            String url = apiUrl + VALIDATE_ENDPOINT + urlParameters;
            try {
                JSONObject json = (JSONObject) new HttpClient(30_000).get(url, Collections.emptyMap(), JSONSerializer::toJSON);
                return json.getBoolean("valid");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                DatadogUtilities.severe(logger, e, "Failed to validate webhook connection");
                return false;
            } catch (Exception e) {
                DatadogUtilities.severe(logger, e, "Failed to validate webhook connection");
                return false;
            }
        }

        @RequirePOST
        public FormValidation doCheckApiKeyCredentialsId(
                @AncestorInPath Item item,
                @QueryParameter("apiKeyCredentialsId") String apiKeyCredentialsId
        ) {
            // Don't validate for users that do not have permission to list credentials
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.ok();
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
            }
            if (StringUtils.isBlank(apiKeyCredentialsId)) {
                return FormValidation.ok();
            }
            if (apiKeyCredentialsId.startsWith("${") && apiKeyCredentialsId.endsWith("}")) {
                return FormValidation.warning("Cannot validate expression based credentials");
            }
            if (CredentialsProvider.listCredentials(StringCredentials.class,
                    item,
                    ACL.SYSTEM,
                    Collections.emptyList(),
                    CredentialsMatchers.withId(apiKeyCredentialsId)).isEmpty()) {
                return FormValidation.error("Cannot find currently selected credentials");
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

        public static Secret getDefaultApiKey() {
            String apiKeyPropertyValue = System.getenv().get(TARGET_API_KEY_PROPERTY);
            return StringUtils.isNotBlank(apiKeyPropertyValue) ? Secret.fromString(apiKeyPropertyValue) : null;
        }
    }
}
