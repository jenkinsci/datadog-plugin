package org.datadog.jenkins.plugins.datadog.model;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.util.Map;

public class StepData {

    private final Map<String, String> envVars;
    private final String nodeName;
    private final String nodeHostname;
    private final String workspace;


    public StepData(final StepContext stepContext){
        this.envVars = DatadogUtilities.getEnvVars(stepContext);
        this.nodeName = DatadogUtilities.getNodeName(stepContext);
        this.nodeHostname = DatadogUtilities.getNodeHostname(stepContext);
        this.workspace = DatadogUtilities.getNodeWorkspace(stepContext);
    }

    public Map<String, String> getEnvVars() {
        return envVars;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getNodeHostname() {
        return nodeHostname;
    }

    public String getWorkspace() {
        return workspace;
    }
}
