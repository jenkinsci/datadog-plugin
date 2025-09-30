package org.datadog.jenkins.plugins.datadog.configuration;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogAgentClient;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogSite;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Symbol("datadogAgentConfiguration")
public class DatadogAgentConfiguration extends DatadogClientConfiguration {

    private static final Logger logger = Logger.getLogger(DatadogAgentConfiguration.class.getName());

    public static final String TARGET_HOST_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_HOST";
    public static final String TARGET_PORT_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_PORT";
    public static final String TARGET_TRACE_COLLECTION_PORT_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_TRACE_COLLECTION_PORT";
    public static final String TARGET_LOG_COLLECTION_PORT_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_LOG_COLLECTION_PORT";
    public static final String DD_AGENT_HOST = "DD_AGENT_HOST";
    public static final String DD_AGENT_PORT = "DD_AGENT_PORT";
    public static final String DD_TRACE_AGENT_PORT = "DD_TRACE_AGENT_PORT";
    public static final String DD_TRACE_AGENT_URL = "DD_TRACE_AGENT_URL";

    static final String DEFAULT_AGENT_HOST_VALUE = "localhost";
    static final Integer DEFAULT_AGENT_PORT_VALUE = 8125;
    static final Integer DEFAULT_TRACE_COLLECTION_PORT_VALUE = 8126;
    static final Integer DEFAULT_LOG_COLLECTION_PORT_VALUE = null;

    private static final int AGENT_CONNECTIVITY_CHECK_TIMEOUT_MILLIS = 2_000;

    private final String agentHost;
    private final Integer agentPort;
    private final Integer agentLogCollectionPort;
    private final Integer agentTraceCollectionPort;

    @DataBoundConstructor
    public DatadogAgentConfiguration(String agentHost, Integer agentPort, Integer agentLogCollectionPort, Integer agentTraceCollectionPort) {
        this.agentHost = agentHost;
        this.agentPort = agentPort;
        this.agentLogCollectionPort = agentLogCollectionPort;
        this.agentTraceCollectionPort = agentTraceCollectionPort;
    }

    /**
     * Invoked by XStream when this object is deserialized.
     * Ensures environment variables have higher priority than configuration persisted on disk
     */
    protected Object readResolve() {
        String agentHost = DatadogAgentConfigurationDescriptor.getAgentHostFromEnvVars(this.agentHost);
        Integer agentPort = DatadogAgentConfigurationDescriptor.getAgentPortFromEnvVars(this.agentPort);
        Integer agentLogCollectionPort = DatadogAgentConfigurationDescriptor.getAgentLogCollectionPortFromEnvVars(this.agentLogCollectionPort);
        Integer agentTraceCollectionPort = DatadogAgentConfigurationDescriptor.getAgentTraceCollectionPortFromEnvVars(this.agentTraceCollectionPort);
        return new DatadogAgentConfiguration(agentHost, agentPort, agentLogCollectionPort, agentTraceCollectionPort);
    }

    public String getAgentHost() {
        return agentHost;
    }

    public Integer getAgentPort() {
        return agentPort;
    }

    public Integer getAgentLogCollectionPort() {
        return agentLogCollectionPort;
    }

    public Integer getAgentTraceCollectionPort() {
        return agentTraceCollectionPort;
    }

    @Override
    public DatadogClient createClient() {
        return new DatadogAgentClient(agentHost, agentPort, agentLogCollectionPort, agentTraceCollectionPort);
    }

    @Override
    public void validateTracesConnection() throws Descriptor.FormException {
        FormValidation connectivityCheckResult = checkTracesConnectivity(agentHost, agentTraceCollectionPort);
        if (connectivityCheckResult.kind == FormValidation.Kind.ERROR) {
            throw new Descriptor.FormException("CI Visibility connectivity check failed: " + connectivityCheckResult.getMessage(), "ciVisibilityData");
        }
    }

    private static FormValidation checkTracesConnectivity(String agentHost, Integer agentTraceCollectionPort) {
        if (StringUtils.isBlank(agentHost)) {
            return FormValidation.error("Host name missing");
        }
        if (agentTraceCollectionPort == null) {
            return FormValidation.error("Trace collection port missing");
        }
        try {
            Set<String> endpoints = DatadogAgentClient.fetchAgentEndpoints(new HttpClient(AGENT_CONNECTIVITY_CHECK_TIMEOUT_MILLIS), agentHost, agentTraceCollectionPort);
            if (!endpoints.isEmpty()) {
                return FormValidation.ok("Success!");
            } else {
                return FormValidation.error("Failed to reach Datadog Agent using host " + agentHost + " and port " + agentTraceCollectionPort);
            }
        } catch (Exception e) {
            return FormValidation.error(e.getMessage());
        }
    }

    @Override
    public void validateLogsConnection() throws Descriptor.FormException {
        if (StringUtils.isBlank(agentHost)) {
            throw new Descriptor.FormException("Logs collection requires agent host to be set", "agentHost");
        }
        if (agentLogCollectionPort == null) {
            throw new Descriptor.FormException("Logs collection requires agent log collection port to be set", "agentLogCollectionPort");
        }
        String errorMessage = checkTcpConnectivity(agentHost, agentLogCollectionPort);
        if (errorMessage != null) {
            throw new Descriptor.FormException("Logs collection connectivity check failed: " + errorMessage, "collectBuildLogs");
        }
    }

    private static String checkTcpConnectivity(final String host, final int port) {
        try (Socket ignored = new Socket(host, port)) {
            return null;
        } catch (Exception ex) {
            DatadogUtilities.severe(logger, ex, "Failed to create socket to host: " + host + ", port: " + port);
            return ex.getMessage();
        }
    }

    @Override
    public Map<String, String> toEnvironmentVariables() {
        if (StringUtils.isBlank(agentHost)) {
            throw new IllegalArgumentException("Agent host is not set");
        }
        if (agentTraceCollectionPort == null) {
            throw new IllegalArgumentException("Traces collection port is not set");
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("DD_AGENT_HOST", agentHost);
        variables.put("DD_TRACE_AGENT_PORT", agentTraceCollectionPort.toString());
        return variables;
    }

    @Nullable
    @Override
    public String getSiteName() {
        return null;
    }

    @Override
    public Descriptor<DatadogClientConfiguration> getDescriptor() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            throw new RuntimeException("Jenkins instance is null");
        }
        return jenkins.getDescriptorOrDie(DatadogAgentConfiguration.class);
    }

    @Extension
    public static final class DatadogAgentConfigurationDescriptor extends DatadogClientConfiguration.DatadogClientConfigurationDescriptor {
        public DatadogAgentConfigurationDescriptor() {
            load();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Use the Datadog Agent to report to Datadog (recommended)";
        }

        @RequirePOST
        public FormValidation doCheckAgentHost(@QueryParameter("agentHost") final String agentHost) {
            if (StringUtils.isBlank(agentHost)) {
                return FormValidation.error("Please enter host value");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doCheckAgentPort(@QueryParameter("agentPort") Integer agentPort) {
            if (agentPort == null) {
                return FormValidation.error("Please enter port value");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doCheckAgentLogCollectionPort(@QueryParameter("agentLogCollectionPort") Integer agentLogCollectionPort,
                                                            @RelativePath("..")
                                                            @QueryParameter("collectBuildLogs") final boolean collectBuildLogs) {
            if (collectBuildLogs && agentLogCollectionPort == null) {
                return FormValidation.error("Log collection is enabled, please enter log collection port value");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doCheckAgentTraceCollectionPort(@QueryParameter("agentTraceCollectionPort") Integer agentTraceCollectionPort,
                                                              @RelativePath("..")
                                                              @QueryParameter("ciVisibilityData") final boolean enableCiVisibility) {
            if (enableCiVisibility && agentTraceCollectionPort == null) {
                return FormValidation.error("CI Visibility is enabled, please enter traces collection port value");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        @SuppressWarnings("lgtm[jenkins/no-permission-check]") // no side effects, no private information returned
        public FormValidation doCheckLogConnectivity(@QueryParameter("agentHost") final String agentHost,
                                                     @QueryParameter("agentLogCollectionPort") Integer agentLogCollectionPort) {
            if (StringUtils.isBlank(agentHost)) {
                return FormValidation.error("Please enter host value");
            }
            if (agentLogCollectionPort == null) {
                return FormValidation.error("Please enter log collection port value");
            }
            String errorMessage = checkTcpConnectivity(agentHost, agentLogCollectionPort);
            if (errorMessage != null) {
                return FormValidation.error("Connectivity check failed: " + errorMessage);
            }
            return FormValidation.ok("Success!");
        }

        @RequirePOST
        @SuppressWarnings("lgtm[jenkins/no-permission-check]") // no side effects, no private information returned
        public FormValidation doCheckTraceConnectivity(@QueryParameter("agentHost") final String agentHost,
                                                       @QueryParameter("agentTraceCollectionPort") Integer agentTraceCollectionPort) {
            return checkTracesConnectivity(agentHost, agentTraceCollectionPort);
        }

        @RequirePOST
        public ListBoxModel doFillSiteItems() {
            DatadogSite[] siteValues = DatadogSite.values();
            ListBoxModel.Option[] values = new ListBoxModel.Option[siteValues.length + 1];
            values[0] = new ListBoxModel.Option("");
            for (int i = 0; i < siteValues.length; i++) {
                values[i + 1] = new ListBoxModel.Option(siteValues[i].name());
            }
            return new ListBoxModel(values);
        }

        public static String getDefaultAgentHost() {
            return getAgentHostFromEnvVars(DEFAULT_AGENT_HOST_VALUE);
        }

        public static String getAgentHostFromEnvVars(String defaultValue) {
            Map<String, String> envVars = System.getenv();

            String host = envVars.get(TARGET_HOST_PROPERTY);
            if (StringUtils.isNotBlank(host)) {
                return host;
            }

            final URL ddTraceAgentUrl = buildURL(envVars.get(DD_TRACE_AGENT_URL));
            if (ddTraceAgentUrl != null) {
                String traceAgentUrlHost = ddTraceAgentUrl.getHost();
                if (StringUtils.isNotBlank(traceAgentUrlHost)) {
                    return traceAgentUrlHost;
                }
            }

            String agentHost = envVars.get(DD_AGENT_HOST);
            if (StringUtils.isNotBlank(agentHost)) {
                return agentHost;
            }
            return defaultValue == null ? DEFAULT_AGENT_HOST_VALUE : defaultValue;
        }

        public static Integer getDefaultAgentPort() {
            return getAgentPortFromEnvVars(DEFAULT_AGENT_PORT_VALUE);
        }

        public static Integer getAgentPortFromEnvVars(Integer defaultValue) {
            Map<String, String> envVars = System.getenv();
            Integer port = getPort(envVars, TARGET_PORT_PROPERTY);
            if (port != null) {
                return port;
            }
            Integer agentPort = getPort(envVars, DD_AGENT_PORT);
            if (agentPort != null) {
                return agentPort;
            }
            return defaultValue == null ? DEFAULT_AGENT_PORT_VALUE : defaultValue;
        }

        public static Integer getDefaultAgentLogCollectionPort() {
            return getAgentLogCollectionPortFromEnvVars(DEFAULT_LOG_COLLECTION_PORT_VALUE);
        }

        public static Integer getAgentLogCollectionPortFromEnvVars(Integer defaultValue) {
            Map<String, String> envVars = System.getenv();
            Integer logsPort = getPort(envVars, TARGET_LOG_COLLECTION_PORT_PROPERTY);
            if (logsPort != null) {
                return logsPort;
            }
            return defaultValue;
        }

        public static Integer getDefaultAgentTraceCollectionPort() {
            return getAgentTraceCollectionPortFromEnvVars(DEFAULT_TRACE_COLLECTION_PORT_VALUE);
        }

        public static Integer getAgentTraceCollectionPortFromEnvVars(Integer defaultValue) {
            Map<String, String> envVars = System.getenv();
            Integer traceCollectionPort = getPort(envVars, TARGET_TRACE_COLLECTION_PORT_PROPERTY);
            if (traceCollectionPort != null) {
                return traceCollectionPort;
            }

            final URL ddTraceAgentUrl = buildURL(envVars.get(DD_TRACE_AGENT_URL));
            if (ddTraceAgentUrl != null) {
                return ddTraceAgentUrl.getPort();
            }

            Integer traceAgentPort = getPort(envVars, DD_TRACE_AGENT_PORT);
            if (traceAgentPort != null) {
                return traceAgentPort;
            }
            return defaultValue == null ? DEFAULT_TRACE_COLLECTION_PORT_VALUE : defaultValue;
        }

        private static URL buildURL(String urlStr) {
            try {
                if (StringUtils.isNotBlank(urlStr)) {
                    return new URL(urlStr);
                }
            } catch (MalformedURLException ex) {
                // ignore
            }
            return null;
        }

        private static Integer getPort(Map<String, String> envVars, String propertyName) {
            String property = envVars.get(propertyName);
            if (StringUtils.isBlank(property)) {
                return null;
            }
            try {
                int port = Integer.parseInt(property);
                if (port >= 0 && port <= 65535) {
                    return port;
                } else {
                    DatadogUtilities.severe(logger, null, "Invalid port value provided in " + propertyName + ": " + port);
                    return null;
                }
            } catch (NumberFormatException e) {
                DatadogUtilities.severe(logger, e, "Failed to parse numeric property " + propertyName + " with value " + property);
                return null;
            }
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
        DatadogAgentConfiguration that = (DatadogAgentConfiguration) o;
        return Objects.equals(agentHost, that.agentHost)
                && Objects.equals(agentPort, that.agentPort)
                && Objects.equals(agentLogCollectionPort, that.agentLogCollectionPort)
                && Objects.equals(agentTraceCollectionPort, that.agentTraceCollectionPort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentHost, agentPort, agentLogCollectionPort, agentTraceCollectionPort);
    }
}
