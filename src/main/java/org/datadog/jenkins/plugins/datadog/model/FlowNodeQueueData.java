package org.datadog.jenkins.plugins.datadog.model;

import java.io.Serializable;

/**
 * Keeps the timestamps of a certain FlowNode based on the onEnterBuildable and onLeaveBuildable callbacks.
 */
public class FlowNodeQueueData implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nodeId;
    private long enterBuildableNanos;
    private long leaveBuildableNanos;
    private long queueTimeNanos = -1L;

    public FlowNodeQueueData(final String nodeId) {
        this.nodeId = nodeId;
    }

    public void setEnterBuildableNanos(long timestampNanos) {
        this.enterBuildableNanos = timestampNanos;
    }

    public void setLeaveBuildableNanos(long timestampNanos) {
        this.leaveBuildableNanos = timestampNanos;
        this.queueTimeNanos = this.leaveBuildableNanos - this.enterBuildableNanos;
    }

    public long getNanosInQueue() {
        return this.queueTimeNanos;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FlowNodeQueueData{");
        sb.append("nodeId='").append(nodeId).append('\'');
        sb.append(", enterBuildableNanos=").append(enterBuildableNanos);
        sb.append(", leaveBuildableNanos=").append(leaveBuildableNanos);
        sb.append(", queueTimeNanos=").append(queueTimeNanos);
        sb.append('}');
        return sb.toString();
    }
}
