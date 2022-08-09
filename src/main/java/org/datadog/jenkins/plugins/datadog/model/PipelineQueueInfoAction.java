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
 */
public class PipelineQueueInfoAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ConcurrentMap<String, FlowNodeQueueData> queueDataByFlowNode;

    public PipelineQueueInfoAction() {
        this.queueDataByFlowNode = new ConcurrentHashMap<>();
    }

    public FlowNodeQueueData get(final Run<?,?> run, String flowNodeId) {
        return this.queueDataByFlowNode.get(flowNodeId);
    }

    public void put(final Run<?,?> run, String flowNodeId, FlowNodeQueueData data) {
        this.queueDataByFlowNode.put(flowNodeId, data);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PipelineQueueInfoAction{");
        sb.append("queueDataByFlowNode=").append(queueDataByFlowNode);
        sb.append('}');
        return sb.toString();
    }
}
