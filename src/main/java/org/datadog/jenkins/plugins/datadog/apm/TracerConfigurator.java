package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import java.util.Map;

interface TracerConfigurator {
    Map<String, String> configure(DatadogTracerJobProperty<?> tracerConfig,
                                  Node node,
                                  FilePath workspacePath,
                                  Map<String, String> envs,
                                  TaskListener listener) throws Exception;

    /**
     * Is used to check whether configuration is valid or needs to be re-applied.
     */
    default boolean isConfigurationValid(Node node, FilePath workspacePath) {
        return true;
    }
}
