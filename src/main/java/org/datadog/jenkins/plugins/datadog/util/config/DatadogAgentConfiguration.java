package org.datadog.jenkins.plugins.datadog.util.config;

import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.DD_AGENT_HOST;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.DD_AGENT_PORT;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.DD_TRACE_AGENT_PORT;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.DD_TRACE_AGENT_URL;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.TARGET_HOST_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.TARGET_PORT_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.TARGET_TRACE_COLLECTION_PORT_PROPERTY;

import org.apache.commons.lang.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class DatadogAgentConfiguration {

    private final String host;
    private final Integer port;
    private final Integer tracesPort;

    public DatadogAgentConfiguration(final String host, final Integer port, final Integer tracesPort) {
        this.host = host;
        this.port = port;
        this.tracesPort = tracesPort;
    }

    public static DatadogAgentConfiguration resolve(final Map<String, String> envVars) {
        final URL ddTraceAgentUrl = buildURL(envVars.get(DD_TRACE_AGENT_URL));

        // Host resolution
        String host = null;
        final String targetHostEnvVar = envVars.get(TARGET_HOST_PROPERTY);
        final String ddAgentHostEnvVar = envVars.get(DD_AGENT_HOST);
        if(StringUtils.isNotBlank(targetHostEnvVar)) {
            host = targetHostEnvVar;
        } else if(ddTraceAgentUrl != null) {
            host = ddTraceAgentUrl.getHost();
        } else if (StringUtils.isNotBlank(ddAgentHostEnvVar)) {
            host = ddAgentHostEnvVar;
        }


        // Port resolution
        Integer port = null;
        final String targetPortEnvVar = envVars.get(TARGET_PORT_PROPERTY);
        final String ddAgentPortEnvVar = envVars.get(DD_AGENT_PORT);
        if (StringUtils.isNotBlank(targetPortEnvVar) && StringUtils.isNumeric(targetPortEnvVar)) {
            port = Integer.parseInt(targetPortEnvVar);
        } else if(StringUtils.isNotBlank(ddAgentPortEnvVar) && StringUtils.isNumeric(ddAgentPortEnvVar)) {
            port = Integer.parseInt(ddAgentPortEnvVar);
        }

        // Traces port resolution
        Integer tracesPort = null;
        final String targetTraceCollectionPortEnvVar = envVars.get(TARGET_TRACE_COLLECTION_PORT_PROPERTY);
        final String ddTraceAgentPortEnvVar = envVars.get(DD_TRACE_AGENT_PORT);
        if(StringUtils.isNotBlank(targetTraceCollectionPortEnvVar) && StringUtils.isNumeric(targetTraceCollectionPortEnvVar)) {
            tracesPort = Integer.parseInt(targetTraceCollectionPortEnvVar);
        } else if(ddTraceAgentUrl != null) {
            tracesPort = ddTraceAgentUrl.getPort();
        } else if(StringUtils.isNotBlank(ddTraceAgentPortEnvVar) && StringUtils.isNumeric(ddTraceAgentPortEnvVar)) {
            tracesPort = Integer.parseInt(ddTraceAgentPortEnvVar);
        }

        return new DatadogAgentConfiguration(host, port, tracesPort);
    }

    private static URL buildURL(final String urlStr) {
        try {
            URL url = null;
            if(StringUtils.isNotBlank(urlStr)) {
                url = new URL(urlStr);
            }
            return url;
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    public String getHost() {
        return this.host;
    }

    public Integer getPort() {
        return this.port;
    }

    public Integer getTracesPort() {
        return this.tracesPort;
    }
}
