/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package org.datadog.jenkins.plugins.datadog.listeners;

import com.cloudbees.workflow.rest.external.FlowNodeExt;
import hudson.Extension;
import hudson.model.Queue;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.audit.DatadogAudit;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.Metrics;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.Status;
import org.datadog.jenkins.plugins.datadog.model.node.NodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.node.StatusAction;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriter;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriterFactory;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * A GraphListener implementation which computes timing information
 * for the various stages in a pipeline.
 */
@Extension
public class DatadogGraphListener implements GraphListener {

    private static final Logger logger = Logger.getLogger(DatadogGraphListener.class.getName());

    @Override
    public void onNewHead(FlowNode flowNode) {
        WorkflowRun run = getRun(flowNode);
        // Filter the node if the job has been excluded from the Datadog plugin configuration.
        if (run == null || !DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
            return;
        }

        if (DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
            processNode(run, flowNode);
        }

        if (!isMonitored(flowNode)) {
            return;
        }

        StepEndNode endNode = (StepEndNode) flowNode;
        StepStartNode startNode = endNode.getStartNode();
        int stageDepth = 0;
        String directParentName = null;
        NodeInfoAction nodeInfo = startNode.getAction(NodeInfoAction.class);
        for (BlockStartNode node : startNode.iterateEnclosingBlocks()) {
            if (DatadogUtilities.isStageNode(node)) {
                if (directParentName == null) {
                    directParentName = getStageName(node);
                }
                if (nodeInfo == null) {
                    nodeInfo = node.getAction(NodeInfoAction.class);
                }
                stageDepth++;
            }
        }
        if (directParentName == null) {
            directParentName = "root";
        }

        DatadogClient client = ClientFactory.getClient();
        if (client == null) {
            return;
        }

        try (Metrics metrics = client.metrics()) {
            String result = DatadogUtilities.getResultTag(endNode);

            String hostname = null;
            if (nodeInfo != null) {
                String nodeHostname = nodeInfo.getNodeHostname();
                if (nodeHostname != null) {
                    hostname = nodeHostname;
                } else if (DatadogUtilities.isMainNode(nodeInfo.getNodeName())) {
                    hostname = DatadogUtilities.getHostname(null);
                }
            }

            BuildData buildData = new BuildData(run, flowNode.getExecution().getOwner().getListener());
            if (hostname == null) {
                hostname = buildData.getHostname(DatadogUtilities.getHostname(null));
            }
            Map<String, Set<String>> tags = buildData.getTags();

            TagsUtil.addTagToTags(tags, "stage_name", getStageName(startNode));
            TagsUtil.addTagToTags(tags, "parent_stage_name", directParentName);
            TagsUtil.addTagToTags(tags, "stage_depth", String.valueOf(stageDepth));
            // Add custom result tag
            TagsUtil.addTagToTags(tags, "result", result);
            long pauseDuration = getPauseDurationMillis(startNode);

            metrics.gauge("jenkins.job.stage_duration", getTime(startNode, endNode), hostname, tags);
            metrics.gauge("jenkins.job.stage_pause_duration", pauseDuration, hostname, tags);
            client.incrementCounter("jenkins.job.stage_completed", hostname, tags);
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Unable to submit the stage duration metric for " + getStageName(startNode));
        }
    }

    private void processNode(WorkflowRun run, FlowNode flowNode) {
        try {
            List<FlowNode> parents = flowNode.getParents();
            for (FlowNode parent : parents) {
                if (parent instanceof StepAtomNode) {
                    // we can only report step node when the next node begins execution,
                    // since we use start time of the next node to compute end time of the step node
                    processNode(run, parent, flowNode);
                }
            }

            if (flowNode instanceof BlockEndNode) {
                processStageNode(run, (BlockEndNode<?>) flowNode);
            }
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Could not process pipeline node " + flowNode.getId() + " (" + flowNode.getDisplayName() + ")");
        }
    }

    private void processStageNode(WorkflowRun run, BlockEndNode<?> blockEndNode) {
        if (!DatadogUtilities.isStageNode(blockEndNode.getStartNode())) {
            return;
        }
        processNode(run, blockEndNode, null);
    }

    private void processNode(WorkflowRun run, FlowNode node, FlowNode nextNode) {
        try {
            BuildPipelineNode pipelineNode = buildPipelineNode(run, node, nextNode);
            propagateStatus(node, nextNode);

            TraceWriter traceWriter = TraceWriterFactory.getTraceWriter();
            if (traceWriter != null) {
                traceWriter.submitPipelineStep(pipelineNode, run);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Interrupted while submitting pipeline trace for node " + node.getDisplayName() + " in run " + (run != null ? run.getDisplayName() : "<null>"));
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Error while submitting pipeline trace for node " + node.getDisplayName() + " in run " + (run != null ? run.getDisplayName() : "<null>"));
        } finally {
            DatadogUtilities.cleanUpTraceActions(node);
        }
    }

    private void propagateStatus(FlowNode flowNode, @Nullable FlowNode nextNode) {
        Status status = getPropagatedStatus(flowNode, nextNode);
        if (status == Status.UNSTABLE) {
            BlockStartNode stageNode = DatadogUtilities.getEnclosingStageNode(flowNode);
            if (stageNode != null) {
                stageNode.addOrReplaceAction(new StatusAction(Status.UNSTABLE, true));
            }

        } else if (status == Status.ERROR) {
            // propagating "error" status is different from propagating "unstable",
            // since error can be caught and suppressed
            for (BlockStartNode enclosingNode : flowNode.iterateEnclosingBlocks()) {
                String catchErrorResult = DatadogUtilities.getCatchErrorResult(enclosingNode);
                if (catchErrorResult != null) {
                    // encountered a "catchError" or a "warnError" block;
                    // will propagate the updated result to the first visible (non-internal) node, and then stop
                    BlockStartNode stageNode = DatadogUtilities.getEnclosingStageNode(enclosingNode);
                    if (stageNode != null) {
                        stageNode.addOrReplaceAction(new StatusAction(Status.fromJenkinsResult(catchErrorResult), false));
                    }
                    break;
                }

                if (isTraceable(enclosingNode)) {
                    enclosingNode.addOrReplaceAction(new StatusAction(status, true));
                    break;
                }
            }
        }
    }

    private static boolean isTraceable(FlowNode node) {
        return node instanceof BlockStartNode && DatadogUtilities.isStageNode(node) || node instanceof StepAtomNode;
    }

    private static Status getPropagatedStatus(FlowNode node, @Nullable FlowNode nextNode) {
        Status nodeStatus = Status.fromJenkinsResult(DatadogUtilities.getResultTag(node));

        StatusAction statusAction;
        if (node instanceof BlockEndNode) {
            BlockStartNode startNode = ((BlockEndNode<?>) node).getStartNode();
            statusAction = startNode.getAction(StatusAction.class);
            return statusAction != null && statusAction.isPropagate() ? Status.combine(nodeStatus, statusAction.getStatus()) : nodeStatus;
        } else { // StepAtomNode
            if (nodeStatus == Status.ERROR) {
                // nextNode is the next node in the execution graph.
                // If node is a step, the next node might be the end of the "script" block that wraps this step.
                // We check if this is the case (display function name is "{")
                if (nextNode != null && "}".equals(nextNode.getDisplayFunctionName()) && nextNode.getError() == null) {
                    // Status is ERROR, but wrapping script block has no error object,
                    // most likely there is a "catch" block in a scripted pipeline,
                    // do not propagate status.
                    return Status.UNKNOWN;
                }
            }
            return nodeStatus;
        }
    }

    private BuildPipelineNode buildPipelineNode(WorkflowRun run, FlowNode node, FlowNode nextNode) {
        long start = System.currentTimeMillis();
        try {
            if (node instanceof StepAtomNode) {
                return new BuildPipelineNode(run, (StepAtomNode) node, nextNode);
            } else if (node instanceof BlockEndNode) {
                BlockEndNode<?> endNode = (BlockEndNode<?>) node;
                return new BuildPipelineNode(run, endNode.getStartNode(), endNode);
            } else {
                throw new IllegalArgumentException("Unexpected flow node type: " + node);
            }

        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("DatadogTracePipelineLogic.buildPipelineNode", start, end);
        }
    }

    private long getPauseDurationMillis(@Nonnull FlowNode startNode) {
        try {
            long pauseDuration = 0;
            FlowGraphWalker walker = new FlowGraphWalker(startNode.getExecution());

            Iterator<FlowNode> it = walker.iterator();

            // Iterates on the execution nodes to sum pause duration of sub-stages.
            // Walks through all the execution graph of startNode, and considers the sub-nodes that are not active
            // anymore. A sub-node is a node for which startNode is a parent (is part of its enclosing blocks).
            while (it.hasNext()) {
                FlowNode node = it.next();
                if (!node.isActive()) {
                    // Lists node parents genealogy, and sees if startNode is one of them.
                    for (BlockStartNode parent : node.iterateEnclosingBlocks()) {
                        if (parent.getId().equals(startNode.getId())) {
                            FlowNodeExt nodeExt = FlowNodeExt.create(node);
                            pauseDuration += nodeExt.getPauseDurationMillis();
                            break;
                        }
                    }
                }
            }

            // In milliseconds
            return pauseDuration;
        } catch (NullPointerException e) {
            logger.warning("Unable to get the stage pause duration");
        }
        return 0;
    }

    private boolean isMonitored(FlowNode flowNode) {
        // Filter the node out if it is not the end of step
        // Timing information is only available once the step has completed.

        // Filter the node out if it is not the end of a stage.
        // The plugin only monitors timing information of stages
        return flowNode instanceof StepEndNode && DatadogUtilities.isStageNode(((StepEndNode) flowNode).getStartNode());
    }

    @CheckForNull
    private WorkflowRun getRun(@Nonnull FlowNode flowNode) {
        Queue.Executable exec;
        try {
            exec = flowNode.getExecution().getOwner().getExecutable();
        } catch (IOException e) {
            // Ignore the error, that step cannot be monitored.
            return null;
        }

        if (exec instanceof WorkflowRun) {
            return (WorkflowRun) exec;
        }
        return null;
    }

    String getStageName(@Nonnull BlockStartNode flowNode) {
        ThreadNameAction threadNameAction = flowNode.getAction(ThreadNameAction.class);
        if (threadNameAction != null) {
            return threadNameAction.getThreadName();
        }
        return flowNode.getDisplayName();
    }

    long getTime(FlowNode startNode, FlowNode endNode) {
        TimingAction startTime = startNode.getAction(TimingAction.class);
        TimingAction endTime = endNode.getAction(TimingAction.class);

        if (startTime != null && endTime != null) {
            return endTime.getStartTime() - startTime.getStartTime();
        }
        return 0;
    }
}
