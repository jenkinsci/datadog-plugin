package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;
import org.datadog.jenkins.plugins.datadog.model.BuildData;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps build span propagation
 */
public class BuildSpanAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

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
