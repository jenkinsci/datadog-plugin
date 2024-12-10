package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogApiConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogClientConfiguration;

@Extension
public class ConnectivityChecksFlare implements FlareContributor {

    @Override
    public int order() {
        return ORDER.CONNECTIVITY_CHECKS;
    }

    @Override
    public String getDescription() {
        return "Connectivity check results";
    }

    @Override
    public String getFilename() {
        return "connectivity-checks.json";
    }

    @Override
    public void writeFileContents(OutputStream out) throws IOException {
        JSONObject payload = new JSONObject();

        DatadogGlobalConfiguration globalConfiguration = DatadogUtilities.getDatadogGlobalDescriptor();
        DatadogClientConfiguration clientConfiguration = globalConfiguration.getDatadogClientConfiguration();

        payload.put("client-type", clientConfiguration.getClass().getSimpleName());
        payload.put("traces-connectivity", validateConnectivity(clientConfiguration::validateTracesConnection));
        payload.put("logs-connectivity", validateConnectivity(clientConfiguration::validateLogsConnection));

        if (clientConfiguration instanceof DatadogApiConfiguration){
            DatadogApiConfiguration apiConfiguration = (DatadogApiConfiguration) clientConfiguration;
            payload.put("api-connectivity", validateConnectivity(apiConfiguration::validateApiConnection));
        }

        String payloadString = payload.toString(2);
        IOUtils.write(payloadString, out, StandardCharsets.UTF_8);
    }

    private String validateConnectivity(ConnectivityValidator validator) {
        try {
            validator.validate();
            return "OK";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @FunctionalInterface
    private interface ConnectivityValidator {
        void validate() throws Exception;
    }
}
