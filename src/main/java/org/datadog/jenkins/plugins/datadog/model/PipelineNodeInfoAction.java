package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

import java.io.Serializable;

public class PipelineNodeInfoAction extends InvisibleAction implements Serializable {

    private final String nodeName;

    public PipelineNodeInfoAction(final String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeName() {
        return nodeName;
    }
}
