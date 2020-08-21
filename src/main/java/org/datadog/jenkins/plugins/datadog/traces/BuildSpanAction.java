package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;
import org.datadog.jenkins.plugins.datadog.model.BuildData;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps build span propagation
 */
public class BuildSpanAction extends InvisibleAction {

    private final BuildData buildData;
    private Map<String, String> buildSpanPropatation;

    public BuildSpanAction(final BuildData buildData){
        this.buildData = buildData;
        this.buildSpanPropatation = new HashMap<>();
    }

    public Map<String, String> getBuildSpanPropatation() {
        return buildSpanPropatation;
    }

    public BuildData getBuildData() {
        return buildData;
    }
}
