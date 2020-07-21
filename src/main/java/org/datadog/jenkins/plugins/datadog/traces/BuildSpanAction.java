package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps build span propagation
 */
public class BuildSpanAction extends InvisibleAction {

    private Map<String, String> buildSpanPropatation;

    protected BuildSpanAction(){
        this.buildSpanPropatation = new HashMap<>();
    }

    public static BuildSpanAction newAction() {
        return new BuildSpanAction();
    }

    public Map<String, String> getBuildSpanPropatation() {
        return buildSpanPropatation;
    }
}
