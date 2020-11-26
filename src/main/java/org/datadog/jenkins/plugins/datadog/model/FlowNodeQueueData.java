package org.datadog.jenkins.plugins.datadog.model;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the timestamps of a certain FlowNode based on the onEnterBuildable and onLeaveBuildable callbacks.
 */
public class FlowNodeQueueData implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nodeId;
    private long enterBuildable;
    private long leaveBuildable;

    public FlowNodeQueueData(final String nodeId) {
        this.nodeId = nodeId;
    }

    public void setEnterBuildable(long timestamp) {
        this.enterBuildable = timestamp;
    }

    public void setLeaveBuildable(long timestamp) {
        this.leaveBuildable = timestamp;
    }

    public long getSecondsInQueue() {
        return TimeUnit.MILLISECONDS.toSeconds(this.leaveBuildable - enterBuildable);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FlowNodeQueueData{");
        sb.append("nodeId=").append(nodeId);
        sb.append(", enterBuildable=").append(enterBuildable);
        sb.append(", leaveBuildable=").append(leaveBuildable);
        sb.append('}');
        return sb.toString();
    }
}
