package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class QueueInfoAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, FlowNodeQueueData> queueDataByFlowNode;

    public QueueInfoAction() {
        this.queueDataByFlowNode = new HashMap<>();
    }

    public FlowNodeQueueData get(String flowNodeId) {
        return this.queueDataByFlowNode.get(flowNodeId);
    }

    public void put(String flowNodeId, FlowNodeQueueData data) {
        this.queueDataByFlowNode.put(flowNodeId, data);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("QueueInfoAction{");
        sb.append("queueDataByFlowNode=").append(queueDataByFlowNode);
        sb.append('}');
        return sb.toString();
    }
}
