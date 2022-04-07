package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;
import hudson.model.Run;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Keeps the Queue Info related to the FlowNode scheduled to be executed.
 *
 * Note: We need to synchronize with the run instance because in parallel pipelines the WorkflowRun.save() method
 * may raise a ConcurrentModificationException if the action is being persisted and it's modified during the process.
 * We synchronize based on the run instance because the WorkflowRun.save() method synchronize on this.
 */
public class PipelineQueueInfoAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ConcurrentMap<String, FlowNodeQueueData> queueDataByFlowNode;

    public PipelineQueueInfoAction() {
        this.queueDataByFlowNode = new ConcurrentHashMap<>();
    }

    public FlowNodeQueueData synchronizedGet(final Run<?,?> run, String flowNodeId) {
        synchronized (run) {
            return this.queueDataByFlowNode.get(flowNodeId);
        }
    }

    public void synchronizedPut(final Run<?,?> run, String flowNodeId, FlowNodeQueueData data) {
        synchronized (run) {
            this.queueDataByFlowNode.put(flowNodeId, data);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PipelineQueueInfoAction{");
        sb.append("queueDataByFlowNode=").append(queueDataByFlowNode);
        sb.append('}');
        return sb.toString();
    }
}
