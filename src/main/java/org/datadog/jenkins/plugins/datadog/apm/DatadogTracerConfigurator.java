package org.datadog.jenkins.plugins.datadog.apm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogClientConfiguration;
import org.datadog.jenkins.plugins.datadog.model.DatadogPluginAction;
import org.datadog.jenkins.plugins.datadog.steps.DatadogPipelineAction;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

public class DatadogTracerConfigurator {

    private final Map<TracerLanguage, TracerConfigurator> configurators;

    public static final DatadogTracerConfigurator INSTANCE = new DatadogTracerConfigurator();

    public DatadogTracerConfigurator() {
        configurators = new EnumMap<>(TracerLanguage.class);
        configurators.put(TracerLanguage.DOTNET, new DotnetConfigurator());
        configurators.put(TracerLanguage.JAVA, new JavaConfigurator());
        configurators.put(TracerLanguage.JAVASCRIPT, new JavascriptConfigurator());
        configurators.put(TracerLanguage.PYTHON, new PythonConfigurator());
        configurators.put(TracerLanguage.RUBY, new RubyConfigurator());
        configurators.put(TracerLanguage.GO, new GoConfigurator());
    }

    private TestOptimization getTestOptimization(FlowNode node, Run<?, ?> run) {
        List<DatadogPipelineAction> actions = DatadogUtilities.getDatadogPipelineActions(run, node);
        for (DatadogPipelineAction action : actions) {
            TestOptimization testOptimization = action.getTestOptimization();
            if (testOptimization != null) {
                return testOptimization;
            }
        }

        Job<?, ?> job = run.getParent();
        DatadogTracerJobProperty<?> tracerJobProperty = job.getProperty(DatadogTracerJobProperty.class);
        if (tracerJobProperty != null) {
            // configured via UI
            return tracerJobProperty.getTestOptimization();
        }

        return null;
    }

    public Map<String, String> configure(Run<?, ?> run, Computer computer, Node node, FlowNode flowNode, EnvVars envs, TaskListener listener) {
        TestOptimization testOptimization = getTestOptimization(flowNode, run);
        if (testOptimization == null || !testOptimization.getEnabled()){
            return Collections.emptyMap();
        }

        TopLevelItem topLevelItem = getTopLevelItem(run);
        FilePath workspacePath = node.getWorkspaceFor(topLevelItem);
        if (workspacePath == null) {
            listener.error("[datadog] Cannot find workspace path for " + topLevelItem + " on " + node);
            return Collections.emptyMap();
        }

        String nodeHostname = DatadogUtilities.getNodeHostname(envs, computer);
        Collection<TracerLanguage> languages = testOptimization.getLanguages();
        for (ConfigureTracerAction action : run.getActions(ConfigureTracerAction.class)) {
            if (nodeHostname != null && nodeHostname.equals(action.nodeHostname) && action.languages.containsAll(languages)) {
                boolean previousConfigurationValid = true;
                for (TracerLanguage language : action.languages) {
                    TracerConfigurator tracerConfigurator = configurators.get(language);
                    if (tracerConfigurator != null) {
                        previousConfigurationValid &= tracerConfigurator.isConfigurationValid(node, workspacePath);
                    }
                }
                if (previousConfigurationValid) {
                    return action.variables;
                }
            }
        }

        DatadogGlobalConfiguration datadogConfig = DatadogUtilities.getDatadogGlobalDescriptor();
        if (datadogConfig == null) {
            listener.error("[datadog] Cannot set up tracer: Datadog config not found");
            return Collections.emptyMap();
        }

        Map<String, String> variables = new HashMap<>(getCommonEnvVariables(datadogConfig, testOptimization));
        for (TracerLanguage language : languages) {
            TracerConfigurator tracerConfigurator = configurators.get(language);
            if (tracerConfigurator == null) {
                listener.error("[datadog] Cannot find tracer configurator for " + language);
                continue;
            }

            try {
                Map<String, String> languageVariables = tracerConfigurator.configure(testOptimization, node, workspacePath, envs, listener);
                variables.putAll(languageVariables);
            } catch (Exception e) {
                ExceptionUtils.printRootCauseStackTrace(e, listener.error("[datadog] Error while configuring " + language + " Datadog Tracer for run " + run + " and node " + node));
                return Collections.emptyMap();
            }
        }
        run.addAction(new ConfigureTracerAction(nodeHostname, languages, variables));
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
                                                             TestOptimization testOptimization) {
        Map<String, String> variables = new HashMap<>();
        variables.put("DD_CIVISIBILITY_AUTO_INSTRUMENTATION_PROVIDER", "jenkins");
        variables.put("DD_CIVISIBILITY_ENABLED", "true");
        variables.put("DD_ENV", "ci");
        variables.put("DD_SERVICE", testOptimization.getServiceName());

        DatadogClientConfiguration clientConfiguration = datadogConfig.getDatadogClientConfiguration();
        Map<String, String> clientEnvironmentVariables = clientConfiguration.toEnvironmentVariables();
        variables.putAll(clientEnvironmentVariables);

        Map<String, String> additionalVariables = testOptimization.getAdditionalVariables();
        if (additionalVariables != null) {
            variables.putAll(additionalVariables);
        }

        return variables;
    }


    private static final class ConfigureTracerAction extends DatadogPluginAction {
        private final String nodeHostname;
        private final Collection<TracerLanguage> languages;
        private final Map<String, String> variables;

        private ConfigureTracerAction(String nodeHostname, @Nonnull Collection<TracerLanguage> languages, Map<String, String> variables) {
            this.nodeHostname = nodeHostname;
            this.languages = languages;
            this.variables = variables;
        }
    }
}
