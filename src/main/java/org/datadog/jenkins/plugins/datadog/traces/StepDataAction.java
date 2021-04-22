package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;
import org.datadog.jenkins.plugins.datadog.model.StepData;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the Step data during a certain Run.
 */
public class StepDataAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, StepData> stepDataByDescriptor = new HashMap<>();

    public StepData put(final FlowNode flowNode, final StepData stepData) {
        return stepDataByDescriptor.put(flowNode.getId(), stepData);
    }

    public StepData get(final FlowNode flowNode) {
        return stepDataByDescriptor.get(flowNode.getId());
    }

}
