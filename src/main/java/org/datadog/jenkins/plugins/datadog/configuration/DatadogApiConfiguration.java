package org.datadog.jenkins.plugins.datadog.configuration;

import static org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogTextApiKey.DatadogTextApiKeyDescriptor.getDefaultKey;

import com.thoughtworks.xstream.annotations.XStreamConverter;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogApiClient;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntake;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogApiKey;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogTextApiKey;
import org.datadog.jenkins.plugins.datadog.util.conversion.PolymorphicReflectionConverter;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

@Symbol("datadogApiConfiguration")
public class DatadogApiConfiguration extends DatadogClientConfiguration {

    @XStreamConverter(PolymorphicReflectionConverter.class)
    private final DatadogIntake intake;

    @XStreamConverter(PolymorphicReflectionConverter.class)
    private final DatadogApiKey apiKey;

    @DataBoundConstructor
    public DatadogApiConfiguration(DatadogIntake intake, DatadogApiKey apiKey) {
        this.intake = intake;
        this.apiKey = apiKey;
    }

    public DatadogIntake getIntake() {
        return intake;
    }

    public DatadogApiKey getApiKey() {
        return apiKey;
    }

    private Secret getApiKeyValue() {
        return apiKey != null ? apiKey.getKey() : null;
    }

    @Override
    public DatadogClient createClient() {
        return new DatadogApiClient(intake.getApiUrl(), intake.getLogsUrl(), intake.getWebhooksUrl(), getApiKeyValue());
    }

    public void validateApiConnection() throws IllegalArgumentException {
        if (intake == null) {
            throw new IllegalArgumentException("CI Visibility requires Datadog intake to be configured");
        }
        String apiUrl = intake.getApiUrl();
        if (StringUtils.isBlank(apiUrl)) {
            throw new IllegalArgumentException("Datadog API URL is not configured");
        }
        Secret apiKeyValue = getApiKeyValue();
        if (apiKeyValue == null){
            throw new IllegalArgumentException("Datadog API key is not configured");
        }
        FormValidation validationResult = DatadogApiKey.validateApiConnection(apiUrl, apiKeyValue);
        if (validationResult.kind != FormValidation.Kind.OK) {
            throw new IllegalArgumentException(validationResult.getMessage());
        }
    }

    @Override
    public void validateTracesConnection() throws Descriptor.FormException {
        if (intake == null) {
            throw new Descriptor.FormException("CI Visibility requires Datadog intake to be configured", "intake");
        }
        String webhooksUrl = intake.getWebhooksUrl();
        if (StringUtils.isBlank(webhooksUrl)) {
            throw new Descriptor.FormException("CI Visibility requires Datadog webhook intake to be configured", "intake");
        }
        checkConnectivity("{}", webhooksUrl, JSONSerializer::toJSON, "webhookIntakeUrl");
    }

    @Override
    public void validateLogsConnection() throws Descriptor.FormException {
        if (intake == null) {
            throw new Descriptor.FormException("Logs collection requires Datadog intake to be configured", "intake");
        }
        String logsUrl = intake.getLogsUrl();
        if (StringUtils.isBlank(logsUrl)) {
            throw new Descriptor.FormException("Logs collection requires Datadog logs intake to be configured", "intake");
        }
        String payload = "{\"message\":\"[datadog-plugin] Check connection\", " +
                "\"ddsource\":\"Jenkins\", \"service\":\"Jenkins\", " +
                "\"hostname\":\"" + DatadogUtilities.getHostname(null) + "\"}";
        checkConnectivity(payload, logsUrl, Function.identity(), "logIntakeUrl");
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
        variables.put("DD_SITE", intake.getSiteName());
        variables.put("DD_API_KEY", Secret.toString(getApiKeyValue()));
        return variables;
    }

    @Nullable
    @Override
    public String getSiteName() {
        return intake.getSiteName();
    }

    @Override
    public Descriptor<DatadogClientConfiguration> getDescriptor() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            throw new RuntimeException("Jenkins instance is null");
        }
        return jenkins.getDescriptorOrDie(DatadogApiConfiguration.class);
    }

    @Extension
    public static final class DatadogApiConfigurationDescriptor extends DatadogClientConfiguration.DatadogClientConfigurationDescriptor {
        public DatadogApiConfigurationDescriptor() {
            load();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Use Datadog site and API key to report to Datadog";
        }

        public static DatadogIntake getDefaultIntake() {
            return DatadogIntake.getDefaultIntake();
        }

        public static DatadogApiKey getDefaultApiKey() {
            return new DatadogTextApiKey(getDefaultKey());
        }

        public List<DatadogIntake.DatadogIntakeDescriptor> getIntakeOptions() {
            return DatadogIntake.DatadogIntakeDescriptor.all();
        }

        public List<DatadogApiKey.DatadogApiKeyDescriptor> getApiKeyOptions() {
            return DatadogApiKey.DatadogApiKeyDescriptor.all();
        }

        @Override
        public int getOrder() {
            return 1;
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
        DatadogApiConfiguration that = (DatadogApiConfiguration) o;
        return Objects.equals(intake, that.intake)
                && Objects.equals(apiKey, that.apiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intake, apiKey);
    }
}
