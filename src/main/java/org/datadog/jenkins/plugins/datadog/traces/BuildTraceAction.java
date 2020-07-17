package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;
import org.datadog.jenkins.plugins.datadog.model.BuildPipeline;

/**
 * Keeps track of build trace.
 */
public class BuildTraceAction extends InvisibleAction {

    private BuildPipeline pipeline;

    protected BuildTraceAction(){
        this.pipeline = BuildPipeline.newPipeline();
    }

    public static BuildTraceAction newAction() {
        return new BuildTraceAction();
    }

    public BuildPipeline getPipeline() {
        return pipeline;
    }
}
