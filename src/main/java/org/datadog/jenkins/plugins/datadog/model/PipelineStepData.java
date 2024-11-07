package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.Run;
import java.util.Map;
import java.util.Set;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.node.DequeueAction;
import org.datadog.jenkins.plugins.datadog.model.node.NodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.node.StatusAction;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Represents a step in a Jenkins Pipeline.
 */
public class PipelineStepData {

    public enum StepType {
        PIPELINE("ci.pipeline", "pipeline"),
        STAGE("ci.stage", "stage"),
        STEP("ci.job", "job");

        private final String tagName;
        private final String buildLevel;

        StepType(final String tagName, final String buildLevel) {
            this.tagName = tagName;
            this.buildLevel = buildLevel;
        }

        public String getTagName() {
            return tagName;
        }

        public String getBuildLevel() {
            return buildLevel;
        }
    }

    private String id;
    private String name;
    private String stageId;
    private String stageName;

    private StepType type;
    private Map<String, Object> args;
    private String workspace;
    private String nodeName;
    private Set<String> nodeLabels;
    private String nodeHostname;
    private long startTimeMillis;
    private long endTimeMillis;
    private long queueTimeMillis;
    private String jenkinsResult;
    private Status status;

    // Throwable of the node.
    // Although the error flag was true, this can be null.
    private Throwable errorObj;
    private String unstableMessage;

    //Tracing
    private long spanId;
    private long parentSpanId = -1;
    private long traceId;

    private final Map<String, Set<String>> tags;

    public PipelineStepData(final Run<?, ?> run, final BlockStartNode startNode, final BlockEndNode<?> endNode) {
        this(run, (FlowNode) startNode, (FlowNode) endNode);

        this.type = StepType.STAGE;

        this.jenkinsResult = DatadogUtilities.getResultTag(endNode);
        this.status = getStatus(startNode, jenkinsResult);
        this.errorObj = DatadogUtilities.getErrorObj(endNode);
        this.unstableMessage = getUnstableMessage(startNode);

        NodeInfoAction nodeInfoAction = startNode.getAction(NodeInfoAction.class);
        if (nodeInfoAction != null) {
            this.nodeName = nodeInfoAction.getNodeName();
            this.nodeHostname = nodeInfoAction.getNodeHostname();
            this.nodeLabels = nodeInfoAction.getNodeLabels();
            this.workspace = nodeInfoAction.getNodeWorkspace();
        }
    }

    public PipelineStepData(final Run<?, ?> run, final StepAtomNode stepNode, final FlowNode nextNode) {
        this(run, (FlowNode) stepNode, (FlowNode) nextNode);

        this.type = StepType.STEP;

        this.jenkinsResult = DatadogUtilities.getResultTag(stepNode);
        this.status = getStatus(stepNode, jenkinsResult);
        this.errorObj = DatadogUtilities.getErrorObj(stepNode);
        this.unstableMessage = getUnstableMessage(stepNode);

        BlockStartNode enclosingStage = DatadogUtilities.getEnclosingStageNode(stepNode);
        if (enclosingStage != null) {
            NodeInfoAction enclosingStageInfoAction = enclosingStage.getAction(NodeInfoAction.class);
            if (enclosingStageInfoAction != null) {
                this.nodeName = enclosingStageInfoAction.getNodeName();
                this.nodeHostname = enclosingStageInfoAction.getNodeHostname();
                this.nodeLabels = enclosingStageInfoAction.getNodeLabels();
                this.workspace = enclosingStageInfoAction.getNodeWorkspace();
            }
        }
    }

    private PipelineStepData(final Run<?, ?> run, FlowNode startNode, FlowNode endNode) {
        TraceInfoAction traceInfoAction = run.getAction(TraceInfoAction.class);
        if (traceInfoAction != null) {
            /*
             * Use "remove-or-create" semantics:
             * - if the ID is there in the action, remove it since it is no longer needed (we're about to submit this node and be done with it)
             * - if the ID is not there, create a new one on the spot without saving it in the action (IDs are initialized lazily, if the node's ID is not there, it means the node had no children that needed to know its ID)
             */
            Long spanId = traceInfoAction.removeOrCreate(startNode.getId());
            if (spanId != null) {
                this.spanId = spanId;
            }
        } else {
            throw new IllegalStateException("Step " + startNode.getId() + " (" + startNode.getDisplayName() + ") has no span info." +
                    "It is possible that CI Visibility was enabled while this step was in progress");
        }

        /*
         * Find node's parent: iterate over the blocks that contain it, starting with the innermost,
         * until we find a block that is included in the trace (a block that corresponds to a stage).
         */
        BlockStartNode enclosingStage = DatadogUtilities.getEnclosingStageNode(startNode);
        if (enclosingStage != null) {
            this.stageId = enclosingStage.getId();
            this.stageName = enclosingStage.getDisplayName();

            Long parentSpanId = traceInfoAction.getOrCreate(enclosingStage.getId());
            if (parentSpanId != null) {
                this.parentSpanId = parentSpanId;
            }
        }

        BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
        if (buildSpanAction != null) {
            TraceSpan.TraceSpanContext traceContext = buildSpanAction.getBuildSpanContext();
            this.traceId = traceContext.getTraceId();

            /*
             * If we didn't find this node's parent previously,
             * then it is a top-level stage, so its parent will be the span that correspond to the build as a whole.
             */
            if (this.parentSpanId == -1) {
                this.parentSpanId = traceContext.getSpanId();
            }
        } else {
            throw new IllegalStateException("Step " + startNode.getId() + " (" + startNode.getDisplayName() + ") has no trace info." +
                    "It is possible that CI Visibility was enabled while this step was in progress");
        }

        DequeueAction queueInfoAction = startNode.getAction(DequeueAction.class);
        if (queueInfoAction != null) {
            this.queueTimeMillis = queueInfoAction.getQueueTimeMillis();
        }

        this.id = startNode.getId();
        this.name = startNode.getDisplayName();
        this.args = ArgumentsAction.getFilteredArguments(startNode);

        // advance start time: queue time should not be considered as part of the build duration
        this.startTimeMillis = DatadogUtilities.getTimeMillis(startNode) + queueTimeMillis;
        if (startTimeMillis < 0) {
            throw new IllegalStateException("Step " + startNode.getId() + " (" + startNode.getDisplayName() + ") has no start time info");
        }

        this.endTimeMillis = DatadogUtilities.getTimeMillis(endNode);
        if (endTimeMillis < 0) {
            throw new IllegalStateException("Step " + endNode.getId() + " (" + endNode.getDisplayName() + ") has no end time info");
        }

        this.tags = DatadogUtilities.getTagsFromPipelineAction(run, startNode);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStageId() {
        return stageId;
    }

    public String getStageName() {
        return stageName;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Set<String> getNodeLabels() {
        return nodeLabels;
    }

    public String getNodeHostname() {
        return nodeHostname;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public long getQueueTimeMillis() {
        return queueTimeMillis;
    }

    public String getJenkinsResult() {
        return jenkinsResult;
    }

    public Status getStatus() {
        return status;
    }

    public Throwable getErrorObj() {
        return errorObj;
    }

    public String getUnstableMessage() {
        return unstableMessage;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isUnstable() {
        return status == Status.UNSTABLE;
    }

    public long getSpanId() {
        return spanId;
    }

    public long getParentSpanId() {
        return parentSpanId;
    }

    public long getTraceId() {
        return traceId;
    }

    public StepType getType() {
        return type;
    }

    public Map<String, Set<String>> getTags() {
        return tags;
    }

    private static Status getStatus(FlowNode node, String jenkinsResult) {
        Status nodeStatus = Status.fromJenkinsResult(jenkinsResult);
        StatusAction statusAction = node.getAction(StatusAction.class);
        return statusAction != null ? Status.combine(nodeStatus, statusAction.getStatus()) : nodeStatus;
    }

    /**
     * Returns the error message for unstable pipelines
     *
     * @return error message
     */
    private static String getUnstableMessage(FlowNode flowNode) {
        final WarningAction warningAction = flowNode.getAction(WarningAction.class);
        return (warningAction != null) ? warningAction.getMessage() : null;
    }
}
