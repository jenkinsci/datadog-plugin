package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps build span propagation
 */
public class BuildSpanAction extends InvisibleAction {

    private Map<String, String> buildSpanPropatation;

    public BuildSpanAction(){
        this.buildSpanPropatation = new HashMap<>();
    }

    public Map<String, String> getBuildSpanPropatation() {
        return buildSpanPropatation;
    }
}
