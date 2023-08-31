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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

public class DatadogTracerConfigurator {

    private static final Logger LOGGER = Logger.getLogger(DatadogTracerConfigurator.class.getName());

    private final Map<TracerLanguage, TracerConfigurator> configurators;

    public static final DatadogTracerConfigurator INSTANCE = new DatadogTracerConfigurator();

    public DatadogTracerConfigurator() {
        configurators = new EnumMap<>(TracerLanguage.class);
        configurators.put(TracerLanguage.JAVA, new JavaConfigurator());
        configurators.put(TracerLanguage.JAVASCRIPT, new JavascriptConfigurator());
        configurators.put(TracerLanguage.PYTHON, new PythonConfigurator());
    }

    public Map<String, String> configure(Run<?, ?> run, Node node, Map<String, String> envs) {
        Job<?, ?> job = run.getParent();
        DatadogTracerJobProperty<?> tracerConfig = job.getProperty(DatadogTracerJobProperty.class);
        if (tracerConfig == null || !tracerConfig.isOn()) {
            return Collections.emptyMap();
        }

        Collection<TracerLanguage> languages = tracerConfig.getLanguages();
        for (ConfigureTracerAction action : run.getActions(ConfigureTracerAction.class)) {
            if (action.node == node && languages.equals(action.languages)) {
                return action.variables;
            }
        }

        DatadogGlobalConfiguration datadogConfig = DatadogUtilities.getDatadogGlobalDescriptor();
        if (datadogConfig == null) {
            LOGGER.log(Level.WARNING, "Cannot set up tracer: Datadog config not found");
            return Collections.emptyMap();
        }

        TopLevelItem topLevelItem = getTopLevelItem(run);
        FilePath workspacePath = node.getWorkspaceFor(topLevelItem);
        if (workspacePath == null) {
            throw new IllegalStateException("Cannot find workspace path for " + topLevelItem + " on " + node);
        }

        Map<String, String> variables = new HashMap<>(getCommonEnvVariables(datadogConfig, tracerConfig));
        for (TracerLanguage language : languages) {
            TracerConfigurator tracerConfigurator = configurators.get(language);
            if (tracerConfigurator == null) {
                LOGGER.log(Level.WARNING, "Cannot find tracer configurator for " + language);
                continue;
            }

            try {
                Map<String, String> languageVariables = tracerConfigurator.configure(tracerConfig, node, workspacePath, envs);
                variables.putAll(languageVariables);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error while configuring " + language + " Datadog Tracer for run " + run + " and node " + node, e);
                return Collections.emptyMap();
            }
        }
        run.addAction(new ConfigureTracerAction(node, languages, variables));
        return variables;
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

    private static Map<String, String> getCommonEnvVariables(DatadogGlobalConfiguration datadogConfig,
                                                             DatadogTracerJobProperty<?> tracerConfig) {
        Map<String, String> variables = new HashMap<>();
        variables.put("DD_CIVISIBILITY_ENABLED", "true");
        variables.put("DD_ENV", "ci");
        variables.put("DD_SERVICE", tracerConfig.getServiceName());

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

        Map<String, String> additionalVariables = tracerConfig.getAdditionalVariables();
        if (additionalVariables != null) {
            variables.putAll(additionalVariables);
        }

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

    private static String getAgentPort(Integer traceCollectionPort) {
        if (traceCollectionPort == null) {
            throw new IllegalArgumentException("Traces collection port is not set");
        } else {
            return traceCollectionPort.toString();
        }
    }

    private static final class ConfigureTracerAction extends InvisibleAction {
        private final Node node;
        private final Collection<TracerLanguage> languages;
        private final Map<String, String> variables;

        private ConfigureTracerAction(Node node, Collection<TracerLanguage> languages, Map<String, String> variables) {
            this.node = node;
            this.languages = languages;
            this.variables = variables;
        }
    }
}
