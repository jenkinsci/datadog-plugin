package org.datadog.jenkins.plugins.datadog.configuration.api.key;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntake;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeSite;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogSite;

public abstract class DatadogApiKey implements Describable<DatadogApiKey>, Serializable {

    private static final Logger logger = Logger.getLogger(DatadogApiKey.class.getName());

    private static final String VALIDATE_ENDPOINT = "v1/validate";

    public abstract Secret getKey();

    public static abstract class DatadogApiKeyDescriptor extends Descriptor<DatadogApiKey> {
        public static List<DatadogApiKeyDescriptor> all() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                throw new RuntimeException("Jenkins instance is null");
            }
            List<DatadogApiKeyDescriptor> descriptors = jenkins.getDescriptorList(DatadogApiKey.class);
            List<DatadogApiKeyDescriptor> sortedDescriptors = new ArrayList<>(descriptors);
            sortedDescriptors.sort(Comparator.comparingInt(DatadogApiKeyDescriptor::getOrder));
            return sortedDescriptors;
        }

        public abstract int getOrder();
    }

    static FormValidation checkConnectivity(Secret apiKeyValue, int intakeIdx, String site, String apiUrl) {
        String validationUrl;
        List<DatadogIntake.DatadogIntakeDescriptor> intakes = DatadogIntake.DatadogIntakeDescriptor.all();
        if (intakes.get(intakeIdx) instanceof DatadogIntakeSite.DatadogIntakeSiteDescriptor) {
            if (StringUtils.isBlank(site)) {
                return FormValidation.error("Please select a site");
            }
            DatadogSite datadogSite = DatadogSite.valueOf(site);
            validationUrl = datadogSite.getApiUrl();
        }  else {
            if (StringUtils.isBlank(apiUrl)) {
                return FormValidation.error("Please fill in the API url");
            }
            validationUrl = apiUrl;
        }
        return validateApiConnection(validationUrl, apiKeyValue);
    }

    public static FormValidation validateApiConnection(String apiUrl, Secret apiKeyValue) {
        String urlParameters = "?api_key=" + Secret.toString(apiKeyValue);
        String url = apiUrl + VALIDATE_ENDPOINT + urlParameters;
        try {
            JSONObject json = (JSONObject) new HttpClient(30_000).get(url, Collections.emptyMap(), JSONSerializer::toJSON);
            if (json.getBoolean("valid")) {
                return FormValidation.ok("Great! Your API key is valid.");
            } else {
                return FormValidation.error("Validation endpoint returned 'false'");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Failed to validate webhook connection");
            return FormValidation.error("Interrupted while validating API key");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to validate webhook connection");
            return FormValidation.error("Error while validating API key: " + e.getMessage());
        }
    }
}
