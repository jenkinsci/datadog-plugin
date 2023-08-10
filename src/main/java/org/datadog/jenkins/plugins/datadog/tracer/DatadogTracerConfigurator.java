package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.util.Secret;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;

public class DatadogTracerConfigurator {

    private static final Logger LOGGER = Logger.getLogger(DatadogTracerConfigurator.class.getName());

    private static final String TRACER_DISTRIBUTION_URL = "https://dtdg.co/latest-java-tracer";
    private static final String TRACER_FILE_NAME = "dd-java-agent.jar";
    private static final String TRACER_JAR_CACHE_TTL_ENV_VAR = "DATADOG_JENKINS_PLUGIN_TRACER_JAR_CACHE_TTL_MINUTES";
    private static final int DEFAULT_TRACER_JAR_CACHE_TTL_MINUTES = 60 * 12;

    private final HttpClient httpClient = new HttpClient(60_000);

    public static final DatadogTracerConfigurator INSTANCE = new DatadogTracerConfigurator();

    public Map<String, String> configure(Run<?, ?> run, Node node, Map<String, String> envs) {
        Job<?, ?> job = run.getParent();
        DatadogTracerJobProperty<?> tracerConfig = job.getProperty(DatadogTracerJobProperty.class);
        if (tracerConfig == null || !tracerConfig.isOn()) {
            return Collections.emptyMap();
        }

        for (ConfigureTracerAction action : run.getActions(ConfigureTracerAction.class)) {
            if (action.node == node) {
                return action.variables;
            }
        }

        Map<String, String> variables = doConfigure(tracerConfig, run, node, envs);
        run.addAction(new ConfigureTracerAction(node, variables));
        return variables;
    }

    private Map<String, String> doConfigure(DatadogTracerJobProperty<?> tracerConfig, Run<?, ?> run, Node node, Map<String, String> envs) {
        try {
            FilePath tracerFile = downloadTracer(run, node);
            return createEnvVariables(tracerConfig, node, tracerFile, envs);
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "Error while configuring Datadog Tracer for run " + run + " and node " + node, e);
            return Collections.emptyMap();
        }
    }

    private FilePath downloadTracer(Run<?, ?> run, Node node) throws Exception {
        TopLevelItem topLevelItem = getTopLevelItem(run);
        FilePath workspacePath = node.getWorkspaceFor(topLevelItem);
        if (workspacePath == null) {
            throw new IllegalStateException("Cannot find workspace path for " + topLevelItem + " on " + node);
        }

        FilePath datadogFolder = workspacePath.child(".datadog");
        datadogFolder.mkdirs();

        FilePath datadogTracerFile = datadogFolder.child(TRACER_FILE_NAME);
        long minutesSinceModification = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - datadogTracerFile.lastModified());
        if (minutesSinceModification < getTracerJarCacheTtlMinutes()) {
            // downloaded tracer is fresh enough
            return datadogTracerFile.absolutize();
        }

        httpClient.getBinary(TRACER_DISTRIBUTION_URL, Collections.emptyMap(), is -> {
            try {
                datadogTracerFile.copyFrom(is);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return datadogTracerFile.absolutize();
    }

    private int getTracerJarCacheTtlMinutes() {
        String envVariable = System.getenv(TRACER_JAR_CACHE_TTL_ENV_VAR);
        if (envVariable != null) {
            try {
                return Integer.parseInt(envVariable);
            } catch (Exception e) {
                // ignored
            }
        }
        return DEFAULT_TRACER_JAR_CACHE_TTL_MINUTES;
    }

    private static TopLevelItem getTopLevelItem(Run<?, ?> run) {
        if (run instanceof AbstractBuild) {
            AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
            AbstractProject<?, ?> project = build.getProject();
            if (project instanceof TopLevelItem) {
                return (TopLevelItem) project;
            } else {
                throw new IllegalArgumentException("Unexpected type of project: " + project);
            }
        } else {
            Job<?, ?> parent = run.getParent();
            if (parent instanceof TopLevelItem) {
                return (TopLevelItem) parent;
            } else {
                throw new IllegalArgumentException("Unexpected type of run parent: " + parent);
            }
        }
    }

    private static Map<String, String> createEnvVariables(DatadogTracerJobProperty<?> tracerConfig, Node node, FilePath tracerFile, Map<String, String> envs) {
        DatadogGlobalConfiguration datadogConfig = DatadogUtilities.getDatadogGlobalDescriptor();
        if (datadogConfig == null) {
            LOGGER.log(Level.INFO, "Cannot set up tracer: Datadog config not found");
            return Collections.emptyMap();
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("DD_CIVISIBILITY_ENABLED", "true");
        variables.put("DD_ENV", "ci");
        variables.put("DD_SERVICE", tracerConfig.getServiceName());

        String tracerAgent = "-javaagent:" + tracerFile.getRemote();
        variables.put("MAVEN_OPTS", prepend(envs, "MAVEN_OPTS", tracerAgent));
        variables.put("GRADLE_OPTS", prepend(envs, "GRADLE_OPTS", "-Dorg.gradle.jvmargs=" + tracerAgent));

        DatadogClient.ClientType clientType = DatadogClient.ClientType.valueOf(datadogConfig.getReportWith());
        switch (clientType) {
            case HTTP:
                variables.put("DD_CIVISIBILITY_AGENTLESS_ENABLED", "true");
                variables.put("DD_SITE", getSite(datadogConfig.getTargetApiURL()));
                variables.put("DD_API_KEY", Secret.toString(datadogConfig.getUsedApiKey()));
                variables.put("DD_APPLICATION_KEY", Secret.toString(datadogConfig.getUsedApplicationKey()));
                break;
            case DSD:
                variables.put("DD_AGENT_HOST", datadogConfig.getTargetHost());
                variables.put("DD_TRACE_AGENT_PORT", getAgentPort(datadogConfig.getTargetTraceCollectionPort()));
                break;
            default:
                throw new IllegalArgumentException("Unexpected client type: " + clientType);
        }

        String proxyConfiguration = getProxyConfiguration(node);
        if (proxyConfiguration != null) {
            variables.put("JAVA_TOOL_OPTIONS", prepend(envs, "JAVA_TOOL_OPTIONS", proxyConfiguration));
        }

        List<DatadogTracerJobProperty.DatadogTracerEnvironmentProperty> additionalVariables = tracerConfig.getAdditionalVariables();
        if (additionalVariables != null) {
            for (DatadogTracerJobProperty.DatadogTracerEnvironmentProperty additionalVariable : additionalVariables) {
                variables.put(additionalVariable.getName(), additionalVariable.getValue());
            }
        }

        // FIXME nikita: see if I can maybe group Tagging and Test Visibility settings in one Datadog section

        return variables;
    }

    private static String prepend(Map<String, String> envs, String propertyName, String propertyValue) {
        String existingPropertyValue = envs.get(propertyName);
        return propertyValue + (existingPropertyValue != null ? " " + existingPropertyValue : "");
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

    private static String getAgentPort(Integer traceCollectionPort) {
        if (traceCollectionPort == null) {
            throw new IllegalArgumentException("Traces collection port is not set");
        } else {
            return traceCollectionPort.toString();
        }
    }

    private static String getProxyConfiguration(Node node) {
        if (!(node instanceof Jenkins)) {
            // only apply Jenkins proxy settings if tracer will be run on master node
            return null;
        }

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }
        hudson.ProxyConfiguration jenkinsProxyConfiguration = jenkins.getProxy();
        if (jenkinsProxyConfiguration == null) {
            return null;
        }

        String proxyHost = jenkinsProxyConfiguration.getName();
        int proxyPort = jenkinsProxyConfiguration.getPort();
        String noProxyHost = jenkinsProxyConfiguration.getNoProxyHost();
        String userName = jenkinsProxyConfiguration.getUserName();
        Secret password = jenkinsProxyConfiguration.getSecretPassword();

        StringBuilder proxyOptions = new StringBuilder();
        if (proxyHost != null) {
            proxyOptions.append("-Dhttp.proxyHost=").append(proxyHost);
        }
        if (proxyPort > 0) {
            proxyOptions.append("-Dhttp.proxyPort=").append(proxyPort);
        }
        if (noProxyHost != null) {
            proxyOptions.append("-Dhttp.nonProxyHosts=").append(noProxyHost);
        }
        if (userName != null) {
            proxyOptions.append("-Dhttp.proxyUser=").append(userName);
        }
        if (password != null) {
            proxyOptions.append("-Dhttp.proxyPassword=").append(Secret.toString(password));
        }
        return proxyOptions.length() > 0 ? proxyOptions.toString() : null;
    }

    private static final class ConfigureTracerAction extends InvisibleAction {
        private final Node node;
        private final Map<String, String> variables;

        private ConfigureTracerAction(Node node, Map<String, String> variables) {
            this.node = node;
            this.variables = variables;
        }
    }

}
