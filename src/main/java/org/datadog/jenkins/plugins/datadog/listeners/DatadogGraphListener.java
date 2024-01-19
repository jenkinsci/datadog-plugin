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

import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_DATE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_EMAIL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_NAME;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_DATE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_EMAIL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_NAME;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_MESSAGE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_TAG;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isCommitInfoAlreadyCreated;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isRepositoryInfoAlreadyCreated;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidRepositoryURL;

import com.cloudbees.workflow.rest.external.FlowNodeExt;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.datadog.jenkins.plugins.datadog.model.StageBreakdownAction;
import org.datadog.jenkins.plugins.datadog.model.StageData;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriter;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriterFactory;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
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
        if (run != null) {
            BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
            if (buildSpanAction != null) {
                BuildData buildData = buildSpanAction.getBuildData();
                if(!DatadogUtilities.isLastNode(flowNode)){
                    final BuildPipelineNode pipelineNode = buildPipelineNode(flowNode);
                    updateStageBreakdown(run, pipelineNode);
                    updateBuildData(buildData, run, pipelineNode, flowNode);
                }
            }
        }

        TraceWriter traceWriter = TraceWriterFactory.getTraceWriter();
        if (traceWriter != null) {
            try {
                traceWriter.submitPipeline(flowNode, run);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                DatadogUtilities.severe(logger, e, "Interrupted while submitting pipeline trace for node " + flowNode.getDisplayName() + " in run " + (run != null ? run.getDisplayName() : "<null>"));
            } catch (Exception e) {
                DatadogUtilities.severe(logger, e, "Error while submitting pipeline trace for node " + flowNode.getDisplayName() + " in run " + (run != null ? run.getDisplayName() : "<null>"));
            }
        }

        DatadogClient client = ClientFactory.getClient();
        if (client == null){
            return;
        }

        if (!isMonitored(flowNode)) {
            return;
        }

        StepEndNode endNode = (StepEndNode) flowNode;
        StepStartNode startNode = endNode.getStartNode();
        int stageDepth = 0;
        String directParentName = null;
        for (BlockStartNode node : startNode.iterateEnclosingBlocks()) {
            if (DatadogUtilities.isStageNode(node)) {
                if(directParentName == null){
                    directParentName = getStageName(node);
                }
                stageDepth++;
            }
        }
        if(directParentName == null){
            directParentName = "root";
        }
        if (run == null){
            return;
        }

        try (Metrics metrics = client.metrics()) {
            String result = DatadogUtilities.getResultTag(endNode);
            BuildData buildData = new BuildData(run, flowNode.getExecution().getOwner().getListener());
            String hostname = buildData.getHostname("");
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

    @Nullable
    private BuildPipelineNode buildPipelineNode(FlowNode flowNode) {
        long start = System.currentTimeMillis();
        try {
            if (flowNode instanceof BlockEndNode) {
                return new BuildPipelineNode((BlockEndNode) flowNode);
            } else if(flowNode instanceof StepAtomNode) {
                return new BuildPipelineNode((StepAtomNode) flowNode);
            } else {
                return null;
            }
        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("DatadogTracePipelineLogic.buildPipelineNode", start, end);
        }
    }

    private void updateBuildData(BuildData buildData, Run<?, ?> run, BuildPipelineNode pipelineNode, FlowNode node) {
        long start = System.currentTimeMillis();
        try {
            if(pipelineNode == null){
                return;
            }

            buildData.setPropagatedMillisInQueue(TimeUnit.NANOSECONDS.toMillis(DatadogUtilities.getNanosInQueue(pipelineNode)));

            final String gitBranch = GitUtils.resolveGitBranch(pipelineNode.getEnvVars(), null);
            if(gitBranch != null && buildData.getBranch("").isEmpty()) {
                buildData.setBranch(gitBranch);
            }

            final String gitUrl = GitUtils.resolveGitRepositoryUrl(pipelineNode.getEnvVars(), null);
            if(gitUrl != null && buildData.getGitUrl("").isEmpty()) {
                buildData.setGitUrl(gitUrl);
            }

            final String gitCommit = GitUtils.resolveGitCommit(pipelineNode.getEnvVars(), null);
            final String buildDataGitCommit = buildData.getGitCommit("");
            if(gitCommit != null && (buildDataGitCommit.isEmpty() || !isValidCommit(buildDataGitCommit))){
                buildData.setGitCommit(gitCommit);
            }

            // Git tag can only be set manually by the user.
            // Otherwise, Jenkins reports it in the branch.
            final String gitTag = pipelineNode.getEnvVars().get(DD_GIT_TAG);
            if(gitTag != null && buildData.getGitTag("").isEmpty()){
                buildData.setGitTag(gitTag);
            }

            // Git data supplied by the user has prevalence. We set them first.
            // Only the data that has not been set will be updated later.
            final String ddGitMessage = pipelineNode.getEnvVars().get(DD_GIT_COMMIT_MESSAGE);
            if(ddGitMessage != null && buildData.getGitMessage("").isEmpty()) {
                buildData.setGitMessage(ddGitMessage);
            }

            final String ddGitAuthorName = pipelineNode.getEnvVars().get(DD_GIT_COMMIT_AUTHOR_NAME);
            if(ddGitAuthorName != null && buildData.getGitAuthorName("").isEmpty()) {
                buildData.setGitAuthorName(ddGitAuthorName);
            }

            final String ddGitAuthorEmail = pipelineNode.getEnvVars().get(DD_GIT_COMMIT_AUTHOR_EMAIL);
            if(ddGitAuthorEmail != null && buildData.getGitAuthorEmail("").isEmpty()) {
                buildData.setGitAuthorEmail(ddGitAuthorEmail);
            }

            final String ddGitAuthorDate = pipelineNode.getEnvVars().get(DD_GIT_COMMIT_AUTHOR_DATE);
            if(ddGitAuthorDate != null && buildData.getGitAuthorDate("").isEmpty()) {
                buildData.setGitAuthorDate(ddGitAuthorDate);
            }

            final String ddGitCommitterName = pipelineNode.getEnvVars().get(DD_GIT_COMMIT_COMMITTER_NAME);
            if(ddGitCommitterName != null && buildData.getGitCommitterName("").isEmpty()){
                buildData.setGitCommitterName(ddGitCommitterName);
            }

            final String ddGitCommitterEmail = pipelineNode.getEnvVars().get(DD_GIT_COMMIT_COMMITTER_EMAIL);
            if(ddGitCommitterEmail != null && buildData.getGitCommitterEmail("").isEmpty()){
                buildData.setGitCommitterEmail(ddGitCommitterEmail);
            }

            final String ddGitCommitterDate = pipelineNode.getEnvVars().get(DD_GIT_COMMIT_COMMITTER_DATE);
            if(ddGitCommitterDate != null && buildData.getGitCommitterDate("").isEmpty()){
                buildData.setGitCommitterDate(ddGitCommitterDate);
            }

            // The Git client will be not null if there is some git information to calculate.
            // We use the same Git client instance to calculate all git information
            // because creating a Git client is a very expensive operation.
            final GitClient gitClient = getGitClient(run, pipelineNode, node, gitUrl, gitCommit);

            if(gitClient != null) {
                final GitCommitAction commitAction = buildGitCommitAction(run, gitClient, pipelineNode);
                if(commitAction != null) {
                    if(buildData.getGitMessage("").isEmpty()){
                        buildData.setGitMessage(commitAction.getMessage());
                    }

                    if(buildData.getGitAuthorName("").isEmpty()) {
                        buildData.setGitAuthorName(commitAction.getAuthorName());
                    }

                    if(buildData.getGitAuthorEmail("").isEmpty()){
                        buildData.setGitAuthorEmail(commitAction.getAuthorEmail());
                    }

                    if(buildData.getGitAuthorDate("").isEmpty()){
                        buildData.setGitAuthorDate(commitAction.getAuthorDate());
                    }

                    if(buildData.getGitCommitterName("").isEmpty()){
                        buildData.setGitCommitterName(commitAction.getCommitterName());
                    }

                    if(buildData.getGitCommitterEmail("").isEmpty()){
                        buildData.setGitCommitterEmail(commitAction.getCommitterEmail());
                    }

                    if(buildData.getGitCommitterDate("").isEmpty()){
                        buildData.setGitCommitterDate(commitAction.getCommitterDate());
                    }
                }
            }

            if(gitClient != null) {
                final GitRepositoryAction repositoryAction = buildGitRepositoryAction(run, gitClient, pipelineNode);
                if(repositoryAction != null) {
                    if(buildData.getGitDefaultBranch("").isEmpty()) {
                        buildData.setGitDefaultBranch(repositoryAction.getDefaultBranch());
                    }
                }
            }

            final String workspace = pipelineNode.getWorkspace();
            if(workspace != null && buildData.getWorkspace("").isEmpty()){
                buildData.setWorkspace(workspace);
            }

            final String nodeName = pipelineNode.getNodeName();
            if(nodeName != null && buildData.getNodeName("").isEmpty()){
                buildData.setNodeName(nodeName);
            }

            final String nodeHostname = pipelineNode.getNodeHostname();
            if(nodeHostname != null && buildData.getHostname("").isEmpty()) {
                buildData.setHostname(nodeHostname);
            }
        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("DatadogTracePipelineLogic.updateBuildData", start, end);
        }
    }

    /**
     * Creates a new Git client only if there is a Git information pending to calculate.
     * This method tries to avoid creating Git clients as much as possible cause it's a very expensive operation.
     * @param run
     * @param pipelineNode
     * @param node
     * @param gitUrl
     * @param gitCommit
     * @return a git client if there is some git information to calculate. In other cases, it returns null.
     */
    private GitClient getGitClient(final Run<?, ?> run, final BuildPipelineNode pipelineNode, final FlowNode node, final String gitUrl, final String gitCommit) {
        GitClient gitClient = null;
        try {
            if(!isValidCommit(gitCommit) && !isValidRepositoryURL(gitUrl)) {
                return null;
            }

            final boolean commitInfoAlreadyCreated = isCommitInfoAlreadyCreated(run, gitCommit);
            final boolean repoInfoAlreadyCreated = isRepositoryInfoAlreadyCreated(run, gitUrl);

            // Only if there is some git information pending to obtain, we create a Git client.
            if(!commitInfoAlreadyCreated || !repoInfoAlreadyCreated) {
                final TaskListener listener = node.getExecution().getOwner().getListener();
                final EnvVars envVars = new EnvVars(pipelineNode.getEnvVars());

                // Create a new Git client is a very expensive operation.
                // Avoid creating Git clients as much as possible.
                gitClient = GitUtils.newGitClient(run, listener, envVars, pipelineNode.getNodeName(), pipelineNode.getWorkspace());
            }
        } catch (Exception ex) {
            logger.fine("Unable to get GitClient. Error: " + ex);
        }
        return gitClient;
    }

    private GitCommitAction buildGitCommitAction(Run<?, ?> run, GitClient gitClient, BuildPipelineNode pipelineNode) {
        try {
            final String gitCommit = GitUtils.resolveGitCommit(pipelineNode.getEnvVars(), null);
            if(!isValidCommit(gitCommit)) {
                return null;
            }

            return GitUtils.buildGitCommitAction(run, gitClient, gitCommit);
        } catch (Exception e) {
            logger.fine("Unable to build GitCommitAction. Error: " + e);
            return null;
        }
    }

    private GitRepositoryAction buildGitRepositoryAction(Run<?, ?> run, GitClient gitClient, BuildPipelineNode pipelineNode) {
        try {
            final String gitRepositoryURL = GitUtils.resolveGitRepositoryUrl(pipelineNode.getEnvVars(), null);
            if(!isValidRepositoryURL(gitRepositoryURL)){
                return null;
            }

            final EnvVars envVars = new EnvVars(pipelineNode.getEnvVars());
            return GitUtils.buildGitRepositoryAction(run, gitClient, envVars, gitRepositoryURL);
        } catch (Exception e) {
            logger.fine("Unable to build GitRepositoryAction. Error: " + e);
            return null;
        }
    }

    private void updateStageBreakdown(final Run<?,?> run, BuildPipelineNode pipelineNode) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
            return;
        }

        long start = System.currentTimeMillis();
        try {
            final StageBreakdownAction stageBreakdownAction = run.getAction(StageBreakdownAction.class);
            if(stageBreakdownAction == null){
                return;
            }

            if(pipelineNode == null){
                return;
            }

            if(!BuildPipelineNode.NodeType.STAGE.equals(pipelineNode.getType())){
                return;
            }

            final StageData stageData = StageData.builder()
                    .withName(pipelineNode.getName())
                    .withStartTimeInMicros(pipelineNode.getStartTimeMicros())
                    .withEndTimeInMicros(pipelineNode.getEndTimeMicros())
                    .build();

            stageBreakdownAction.put(stageData.getName(), stageData);
        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("DatadogTracePipelineLogic.updateStageBreakdown", start, end);
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
        if (!(flowNode instanceof StepEndNode)) {
            return false;
        }

        // Filter the node if the job has been excluded from the Datadog plugin configuration.
        WorkflowRun run = getRun(flowNode);
        if (run == null || !DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
            return false;
        }

        // Filter the node out if it is not the end of a stage.
        // The plugin only monitors timing information of stages
        if (!DatadogUtilities.isStageNode(((StepEndNode) flowNode).getStartNode())) {
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

    /**
     * Gets the jenkins run object of the specified executing workflow.
     *
     * @param exec execution of a workflow
     * @return jenkins run object of a job
     */
    private static @CheckForNull Run<?, ?> runFor(FlowExecution exec) {
        Queue.Executable executable;
        try {
            executable = exec.getOwner().getExecutable();
        } catch (IOException x) {
            DatadogUtilities.severe(logger, x, "Failed to get Jenkins executable");
            return null;
        }
        if (executable instanceof Run) {
            return (Run<?, ?>) executable;
        } else {
            return null;
        }
    }
}
