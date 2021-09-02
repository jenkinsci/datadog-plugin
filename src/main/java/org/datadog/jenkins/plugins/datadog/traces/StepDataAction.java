package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;
import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.model.StepData;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the Step data during a certain Run.
 *
 * Note: We need to synchronize with the run instance because in parallel pipelines the WorkflowRun.save() method
 * may raise a ConcurrentModificationException if the action is being persisted and it's modified during the process.
 * We synchronize based on the run instance because the WorkflowRun.save() method synchronize on this.
 */
public class StepDataAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, StepData> stepDataByDescriptor = new HashMap<>();

    public StepData synchronizedPut(final Run<?,?> run, final FlowNode flowNode, final StepData stepData) {
        synchronized (run){
            return stepDataByDescriptor.put(flowNode.getId(), stepData);
        }
    }

    public StepData synchronizedGet(final Run<?,?> run, final FlowNode flowNode) {
        synchronized (run){
            return stepDataByDescriptor.get(flowNode.getId());
        }
    }

}
