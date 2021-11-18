package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;

import java.io.Serializable;

/**
 * Keeps build span propagation
 */
public class BuildSpanAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final BuildData buildData;
    private final TraceSpan.TraceSpanContext buildSpanContext;

    public BuildSpanAction(final BuildData buildData, final TraceSpan.TraceSpanContext buildSpanContext){
       this.buildData = buildData;
       this.buildSpanContext = buildSpanContext;
    }

    public BuildData getBuildData() {
        return buildData;
    }

    public TraceSpan.TraceSpanContext getBuildSpanContext() {
        return buildSpanContext;
    }
}
