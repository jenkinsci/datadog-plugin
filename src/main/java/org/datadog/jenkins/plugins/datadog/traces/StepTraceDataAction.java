package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;
import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.model.StepTraceData;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class StepTraceDataAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ConcurrentMap<String, StepTraceData> stepTraceDataByDescriptor = new ConcurrentHashMap<>();

    public StepTraceData put(final Run<?,?> run, final FlowNode flowNode, final StepTraceData stepTraceData) {
        return stepTraceDataByDescriptor.put(flowNode.getId(), stepTraceData);
    }

    public StepTraceData get(final Run<?,?> run, final FlowNode flowNode) {
        return stepTraceDataByDescriptor.get(flowNode.getId());
    }

}
