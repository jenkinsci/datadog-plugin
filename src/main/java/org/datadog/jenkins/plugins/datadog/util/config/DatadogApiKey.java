package org.datadog.jenkins.plugins.datadog.util.config;

import hudson.DescriptorExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.Secret;
import java.io.Serializable;
import java.util.Collections;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;

public abstract class DatadogApiKey implements Describable<DatadogApiKey>, Serializable {

    private static final Logger logger = Logger.getLogger(DatadogApiKey.class.getName());

    private static final String VALIDATE_ENDPOINT = "v1/validate";

    public abstract Secret getKey();

    public static abstract class DatadogApiKeyDescriptor extends Descriptor<DatadogApiKey> {
        public static DescriptorExtensionList<DatadogApiKey, DatadogApiKeyDescriptor> all() {
            return Jenkins.getInstanceOrNull().getDescriptorList(DatadogApiKey.class);
        }
    }

    static boolean validateApiConnection(String apiUrl, Secret apiKeyValue) {
        String urlParameters = "?api_key=" + Secret.toString(apiKeyValue);
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
}
