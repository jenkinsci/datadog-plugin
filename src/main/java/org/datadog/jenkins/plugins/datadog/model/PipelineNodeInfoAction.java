package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

import java.io.Serializable;
import java.util.Set;

public class PipelineNodeInfoAction extends InvisibleAction implements Serializable {

    private final String nodeName;
    private final Set<String> nodeLabels;

    private final String nodeHostname;

    public PipelineNodeInfoAction(final String nodeName, final Set<String> nodeLabels, final String nodeHostname) {
        this.nodeName = nodeName;
        this.nodeLabels = nodeLabels;
        this.nodeHostname = nodeHostname;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Set<String> getNodeLabels() {
        return nodeLabels;
    }

    public String getNodeHostname() {
        return nodeHostname;
    }
}
