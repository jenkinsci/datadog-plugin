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

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Result;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction;
import org.jenkinsci.plugins.workflow.actions.QueueItemAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * A GraphListener implementation which computes timing information for the
 * various stages in a pipeline.
 */
@Extension
public class DatadogGraphListener implements GraphListener {

    private static final Logger logger = Logger.getLogger(DatadogGraphListener.class.getName());

    @Override
    public void onNewHead(FlowNode flowNode) {
        if (!isMonitored(flowNode)) {
            return;
        }
        DatadogClient client = ClientFactory.getClient();
        if client == null){
            return;
        }
        StepEndNode endNode = (StepEndNode) flowNode;
        StepStartNode startNode = endNode.getStartNode();
        int stageDepth = 0;
        String directParentName = null;
        for (BlockStartNode node : startNode.iterateEnclosingBlocks()) {
            if (isStageNode(node)) {
                if(directParentName == null){
                    directParentName = getStageName(node);
                }
                stageDepth++;
            }
        }
        if(directParentName == null){
            directParentName = "root";
        }
        WorkflowRun run = getRun(flowNode);
        if(run == null){
            return;
        }
        try {
            String result = getResultTag(endNode);
            BuildData buildData = new BuildData(run, flowNode.getExecution().getOwner().getListener());
            String hostname = buildData.getHostname("");
            Map<String, Set<String>> tags = buildData.getTags();
            TagsUtil.addTagToTags(tags, "stage_name", getStageName(startNode));
            TagsUtil.addTagToTags(tags, "parent_stage_name", directParentName);
            TagsUtil.addTagToTags(tags, "stage_depth", String.valueOf(stageDepth));
            // Add custom result tag
            TagsUtil.addTagToTags(tags, "result", result);
            client.gauge("jenkins.job.stage_duration", getTime(startNode, endNode), hostname, tags);
        } catch (IOException | InterruptedException e) {
            DatadogUtilities.severe(logger, e, "Unable to submit the stage duration metric for " + getStageName(startNode));
        }
    }

    private boolean isMonitored(FlowNode flowNode) {
        // Filter the node out if it is not the end of step
        // Timing information is only available once the step has completed.
        if (!(flowNode instanceof StepEndNode)) {
            return false;
        }

        // Filter the node if the job has been blacklisted from the Datadog plugin configuration.
        WorkflowRun run = getRun(flowNode);
        if (run == null || !DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
            return false;
        }

        // Filter the node out if it is not the end of a stage.
        // The plugin only monitors timing information of stages
        if (!isStageNode(((StepEndNode) flowNode).getStartNode())) {
            return false;
        }

        // Finally return true as this node is the end of a monitored stage.
        return true;
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

    boolean isStageNode(BlockStartNode flowNode) {
        if (flowNode == null) {
            return false;
        }
        if (flowNode.getAction(StageAction.class) != null) {
            // Legacy style stage block without a body
            // https://groups.google.com/g/jenkinsci-users/c/MIVk-44cUcA
            return true;
        }
        if (flowNode.getAction(ThreadNameAction.class) != null) {
            // TODO comment
            return false;
        }
        return flowNode.getAction(LabelAction.class) != null;
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

    String getResultTag(@Nonnull FlowNode endNode) {
        ErrorAction error = endNode.getError();
        if (error != null) {
            return "ERROR";
        }
        WarningAction warningAction = endNode.getPersistentAction(WarningAction.class);
        if (warningAction != null) {
            Result result = warningAction.getResult();
            // Result could be SUCCESS, NOT_BUILT, FAILURE, etc https://javadoc.jenkins-ci.org/hudson/model/Result.html
            return result.toString();
        }
        // Other possibilities are queued, launched, unknown: https://javadoc.jenkins.io/plugin/workflow-api/org/jenkinsci/plugins/workflow/actions/QueueItemAction.QueueState.html
        if (QueueItemAction.getNodeState(endNode) == QueueItemAction.QueueState.CANCELLED) {
            return "CANCELLED";
        }
        FlowExecution exec = endNode.getExecution();
        if ((exec != null && exec.isComplete()) || NotExecutedNodeAction.isExecuted(endNode)) {
            return "SUCCESS";
        }
        return "UNKNOWN";
    }
}
