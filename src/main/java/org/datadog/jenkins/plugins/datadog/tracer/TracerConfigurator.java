package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.FilePath;
import hudson.model.Node;
import java.util.Map;

interface TracerConfigurator {
    Map<String, String> configure(DatadogTracerJobProperty<?> tracerConfig,
                                  Node node,
                                  FilePath workspacePath,
                                  Map<String, String> envs) throws Exception;
}
