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
    private final TraceSpan.TraceSpanContext buildSpanContext;

    //TODO Remove Java Tracer
    private Map<String, String> buildSpanPropatationOld;


    public BuildSpanAction(final BuildData buildData, final TraceSpan.TraceSpanContext buildSpanContext){
        this.buildData = buildData;
        this.buildSpanPropatationOld = new HashMap<>();

       this.buildSpanContext = buildSpanContext;
    }

    //TODO Remove Java Tracer
    public Map<String, String> getBuildSpanPropatationOld() {
        return buildSpanPropatationOld;
    }

    public BuildData getBuildData() {
        return buildData;
    }

    public TraceSpan.TraceSpanContext getBuildSpanContext() {
        return buildSpanContext;
    }
}
