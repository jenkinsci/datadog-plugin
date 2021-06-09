package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

public class PipelineNodeInfoAction extends InvisibleAction implements Serializable {

    private final String nodeName;
    private final Set<String> nodeLabels;

    public PipelineNodeInfoAction(final String nodeName, final Set<String> nodeLabels) {
        this.nodeName = nodeName;
        this.nodeLabels = nodeLabels;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Set<String> getNodeLabels() {
        return nodeLabels;
    }
}
