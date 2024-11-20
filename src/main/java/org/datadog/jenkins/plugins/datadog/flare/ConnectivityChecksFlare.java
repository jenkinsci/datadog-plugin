package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogApiClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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

        // TODO rework the checks below following configuration refactoring
        DatadogGlobalConfiguration globalConfiguration = DatadogUtilities.getDatadogGlobalDescriptor();
        DatadogClient.ClientType clientType = DatadogClient.ClientType.valueOf(globalConfiguration.getReportWith());

        if (clientType == DatadogClient.ClientType.DSD) {
            payload.put("client-type", DatadogClient.ClientType.DSD);
            payload.put("logs-connectivity", globalConfiguration.doCheckAgentConnectivityLogs(globalConfiguration.getTargetHost(), String.valueOf(globalConfiguration.getTargetLogCollectionPort())).toString());
            payload.put("traces-connectivity", globalConfiguration.doCheckAgentConnectivityTraces(globalConfiguration.getTargetHost(), String.valueOf(globalConfiguration.getTargetTraceCollectionPort())).toString());

        } else if (clientType == DatadogClient.ClientType.HTTP) {
            payload.put("client-type", DatadogClient.ClientType.HTTP);
            payload.put("api-connectivity", DatadogApiClient.validateDefaultIntakeConnection(globalConfiguration.getTargetApiURL(), globalConfiguration.getUsedApiKey()));
            payload.put("logs-connectivity", DatadogApiClient.validateLogIntakeConnection(globalConfiguration.getTargetLogIntakeURL(), globalConfiguration.getUsedApiKey()));
            payload.put("traces-connectivity", DatadogApiClient.validateWebhookIntakeConnection(globalConfiguration.getTargetWebhookIntakeURL(), globalConfiguration.getUsedApiKey()));

        } else {
            throw new IllegalArgumentException("Unsupported client type: " + clientType);
        }

        String payloadString = payload.toString(2);
        IOUtils.write(payloadString, out, StandardCharsets.UTF_8);
    }
}
