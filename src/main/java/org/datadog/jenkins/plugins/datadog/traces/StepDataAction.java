package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;
import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.model.StepData;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Keeps the Step data during a certain Run.
 */
public class StepDataAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ConcurrentMap<String, StepData> stepDataByDescriptor = new ConcurrentHashMap<>();

    public StepData put(final Run<?,?> run, final FlowNode flowNode, final StepData stepData) {
        return stepDataByDescriptor.put(flowNode.getId(), stepData);
    }

    public StepData get(final Run<?,?> run, final FlowNode flowNode) {
        return stepDataByDescriptor.get(flowNode.getId());
    }

}
