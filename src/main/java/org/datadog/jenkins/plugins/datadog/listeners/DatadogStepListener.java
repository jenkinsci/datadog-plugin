package org.datadog.jenkins.plugins.datadog.listeners;

import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_DATE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_EMAIL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_NAME;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_DATE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_EMAIL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_NAME;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_MESSAGE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_DEFAULT_BRANCH;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_TAG;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Run;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.audit.DatadogAudit;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.node.NodeInfoAction;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriter;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriterFactory;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;

@Extension
public class DatadogStepListener implements StepListener {

    private static final Logger logger = Logger.getLogger(DatadogStepListener.class.getName());

    @Override
    public void notifyOfNewStep(@Nonnull Step step, @Nonnull StepContext context) {
        try {
            final Run<?,?> run = context.get(Run.class);
            if (run == null) {
                logger.severe("Unable to store Step data for step '" + step + "'. Run is null");
                return;
            }

            final FlowNode flowNode = context.get(FlowNode.class);
            if(flowNode == null) {
                logger.severe("Unable to store Step data in Run '"+run.getFullDisplayName()+"'. FlowNode is null");
                return;
            }

            if(!(flowNode instanceof StepAtomNode)){
                return;
            }

            Map<String, String> envVars = getEnvVars(context);
            updateGitData(run, envVars);
            updateBuildData(run, envVars);

            String nodeName = getNodeName(context);
            String nodeHostname = getNodeHostname(context);
            Set<String> nodeLabels = getNodeLabels(context);
            String nodeWorkspace = getNodeWorkspace(context);
            String executorNumber = envVars.get("EXECUTOR_NUMBER");
            NodeInfoAction nodeInfoAction = new NodeInfoAction(nodeName, nodeHostname, nodeLabels, nodeWorkspace, executorNumber);

            if (DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
                // propagate node info to stage node
                BlockStartNode stageNode = DatadogUtilities.getEnclosingStageNode(flowNode);
                if (stageNode != null) {
                    stageNode.addOrReplaceAction(nodeInfoAction);
                }
            }

            // We use the PipelineNodeInfoAction to propagate
            // the correct node name to the root span (ci.pipeline).

            // Check if the pipeline node info has been stored in previous steps.
            // If so, there is no need to search this information again.
            final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
            if(pipelineNodeInfoAction != null) {
                return;
            }

            // If the first 'Allocate node : Start' flow node
            // is a direct child of the `Start of Pipeline` flow node, the reported node name
            // for this Step belongs also to the `Start of Pipeline` flow node,
            // meaning, it's the node name for the root span (ci.pipeline).

            // Starting from the current flow node (which represents the step),
            // we try to find the first 'Allocate node : Start' node through its parents.
            final FlowNode firstAllocateNodeStart = findFirstAllocateNodeStart(flowNode);
            if(firstAllocateNodeStart == null){
                return;
            }

            // If the parent block from the first 'Allocate node : Start' node is the 'Start of Pipeline' node
            // the worker node where this Step was executed will be the worker node for the pipeline.
            findStartOfPipeline(run, nodeInfoAction, firstAllocateNodeStart);

        } catch (Exception ex) {
            logger.severe("Unable to extract Run information of the StepContext. " + ex);
        }
    }

    /**
     * Returns the nodeName of the remote node which is executing a determined {@code Step}
     * @param stepContext
     * @return node name of the remote node.
     */
    private static String getNodeName(StepContext stepContext) {
        try {
            Computer computer = stepContext.get(Computer.class);
            return DatadogUtilities.getNodeName(computer);
        } catch (Exception e){
            logger.fine("Unable to extract the node name from StepContext.");
            return null;
        }
    }

    /**
     * Returns the hostname of the remote node which is executing a determined {@code Step}
     * See {@code Computer.getHostName()}
     * @param stepContext
     * @return hostname of the remote node.
     */
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    private static String getNodeHostname(final StepContext stepContext) {
        return DatadogUtilities.getNodeHostname(getSafely(stepContext, EnvVars.class), getSafely(stepContext, Computer.class));
    }

    @Nullable
    private static <T> T getSafely(final StepContext stepContext, final Class<T> type) {
        try {
            return stepContext.get(type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the nodeLabels of the remote node which is executing a determined {@code Step}
     * @param stepContext
     * @return node labels of the remote node.
     */
    private static Set<String> getNodeLabels(StepContext stepContext) {
        try {
            Computer computer = stepContext.get(Computer.class);
            return DatadogUtilities.getNodeLabels(computer);
        } catch (Exception e) {
            logger.fine("Unable to extract the node labels from StepContext.");
            return Collections.emptySet();
        }
    }

    /**
     * Returns the workspace filepath of the remote node which is executing a determined {@code Step}
     * @return absolute filepath of the workspace of the remote node.
     */
    private static String getNodeWorkspace(final StepContext stepContext) {
        FilePath filePath = null;
        try {
            filePath = stepContext.get(FilePath.class);
        } catch (Exception e){
            logger.fine("Unable to extract FilePath information of the StepContext.");
        }

        if(filePath == null) {
            return null;
        }

        return filePath.getRemote();
    }

    /**
     * Returns {@code Map<String,String>} with environment variables of a certain {@code StepContext}
     * @return map with environment variables of a stepContext.
     */
    private static Map<String, String> getEnvVars(StepContext stepContext) {
        EnvVars envVarsObj = null;
        try {
            envVarsObj = stepContext.get(EnvVars.class);
        } catch (Exception e){
            logger.fine("Unable to extract environment variables from StepContext.");
        }

        if(envVarsObj == null) {
            return Collections.emptyMap();
        }
        return envVarsObj.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void updateBuildData(Run<?, ?> run, Map<String, String> envVars) {
        BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
        if (buildSpanAction == null) {
            return;
        }
        String buildUrl = envVars.get("BUILD_URL");
        if (buildUrl != null) {
            buildSpanAction.setBuildUrl(buildUrl);
        }
    }

    /**
     * Examine the step's environment to see if it contains any variables that hold git-related data.
     * It could be variables that are set manually by the pipeline authors (such variables will have {@code DD_} prefix),
     * or variables that are automatically set by the Jenkins Git Plugin.
     * <p>
     * Whatever data we manage to extract, we save in {@link GitCommitAction} that is associated with the pipeline.
     * It'll later be used to populate git tags both in the pipeline span, and in the spans that correspond to other pipeline steps.
     * <p>
     * The reason we examine step environment, rather than checking the pipeline environment (even though the pipeline has its own copy of {@link EnvVars})
     * is that the pipeline env is minimal and misses many env vars, including the ones that are set manually,
     * while the step env contains much more data.
     */
    private static void updateGitData(Run<?, ?> run, Map<String, String> envVars) {
        GitCommitAction commitAction = run.getAction(GitCommitAction.class);
        if (commitAction != null) {
            // Git tag can only be set manually by the user.
            // Otherwise, Jenkins reports it in the branch.
            final String gitTag = envVars.get(DD_GIT_TAG);
            if(gitTag != null){
                commitAction.setTag(gitTag);
            }

            final String gitCommit = GitUtils.resolveGitCommit(envVars);
            if(gitCommit != null) {
                commitAction.setCommit(gitCommit);
            }

            // Git data supplied by the user has prevalence. We set them first.
            // Only the data that has not been set will be updated later.
            final String ddGitMessage = envVars.get(DD_GIT_COMMIT_MESSAGE);
            if(ddGitMessage != null) {
                commitAction.setMessage(ddGitMessage);
            }

            final String ddGitAuthorName = envVars.get(DD_GIT_COMMIT_AUTHOR_NAME);
            if(ddGitAuthorName != null) {
                commitAction.setAuthorName(ddGitAuthorName);
            }

            final String ddGitAuthorEmail = envVars.get(DD_GIT_COMMIT_AUTHOR_EMAIL);
            if(ddGitAuthorEmail != null) {
                commitAction.setAuthorEmail(ddGitAuthorEmail);
            }

            final String ddGitCommitterName = envVars.get(DD_GIT_COMMIT_COMMITTER_NAME);
            if(ddGitCommitterName != null) {
                commitAction.setCommitterName(ddGitCommitterName);
            }

            final String ddGitCommitterEmail = envVars.get(DD_GIT_COMMIT_COMMITTER_EMAIL);
            if(ddGitCommitterEmail != null) {
                commitAction.setCommitterEmail(ddGitCommitterEmail);
            }

            final String ddGitAuthorDate = envVars.get(DD_GIT_COMMIT_AUTHOR_DATE);
            if (StringUtils.isNotBlank(ddGitAuthorDate)) {
                if (DatadogUtilities.isValidISO8601Date(ddGitAuthorDate)) {
                    commitAction.setAuthorDate(ddGitAuthorDate);
                } else {
                    logger.log(Level.WARNING, "Invalid date specified in " + DD_GIT_COMMIT_AUTHOR_DATE + ": expected ISO8601 format (" + DatadogUtilities.toISO8601(new Date()) + "), got " + ddGitAuthorDate);
                }
            }

            final String ddGitCommitterDate = envVars.get(DD_GIT_COMMIT_COMMITTER_DATE);
            if (StringUtils.isNotBlank(ddGitCommitterDate)) {
                if (DatadogUtilities.isValidISO8601Date(ddGitCommitterDate)) {
                    commitAction.setCommitterDate(ddGitCommitterDate);
                } else {
                    logger.log(Level.WARNING, "Invalid date specified in " + DD_GIT_COMMIT_COMMITTER_DATE + ": expected ISO8601 format (" + DatadogUtilities.toISO8601(new Date()) + "), got " + ddGitCommitterDate);
                }
            }
        }

        GitRepositoryAction repositoryAction = run.getAction(GitRepositoryAction.class);
        if (repositoryAction != null) {
            final String gitUrl = GitUtils.resolveGitRepositoryUrl(envVars);
            if (gitUrl != null && !gitUrl.isEmpty()) {
                repositoryAction.setRepositoryURL(gitUrl);
            }

            final String defaultBranch = GitInfoUtils.normalizeBranch(envVars.get(DD_GIT_DEFAULT_BRANCH));
            if (defaultBranch != null && !defaultBranch.isEmpty()) {
                repositoryAction.setDefaultBranch(defaultBranch);
            }

            final String gitBranch = GitUtils.resolveGitBranch(envVars);
            if(gitBranch != null && !gitBranch.isEmpty()) {
                repositoryAction.setBranch(gitBranch);
            }
        }
    }

    private void findStartOfPipeline(final Run<?,?> run, final NodeInfoAction nodeInfoAction, final FlowNode firstAllocateNodeStart) {
        long start = System.currentTimeMillis();
        try {
            final Iterator<BlockStartNode> blockStartNodes = firstAllocateNodeStart.iterateEnclosingBlocks().iterator();
            if(blockStartNodes.hasNext()) {
                final FlowNode candidate = blockStartNodes.next();
                if("Start of Pipeline".equals(candidate.getDisplayName())) {
                    PipelineNodeInfoAction pipelineNodeInfoAction = new PipelineNodeInfoAction(
                        nodeInfoAction.getNodeName() != null ? nodeInfoAction.getNodeName() : "master",
                        nodeInfoAction.getNodeLabels(), nodeInfoAction.getNodeHostname(),
                        nodeInfoAction.getNodeWorkspace(),
                        nodeInfoAction.getExecutorNumber());
                    run.addOrReplaceAction(pipelineNodeInfoAction);

                    if (DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
                        // we have node info available now - submit a pipeline event so the backend could update its data
                        submitPipelineData(run);
                    }
                }
            }
        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("DatadogStepListener.findStartOfPipeline", start, end);
        }
    }

    private static void submitPipelineData(Run<?, ?> run) {
        TraceWriter traceWriter = TraceWriterFactory.getTraceWriter();
        if (traceWriter == null) {
            return;
        }
        try {
            BuildData buildData = new BuildData(run, null);
            traceWriter.submitBuild(buildData, run);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Interrupted while trying to submit pipeline data update");

        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to submit pipeline data update");
        }
    }

    private FlowNode findFirstAllocateNodeStart(FlowNode current) {
        long start = System.currentTimeMillis();
        try {
            for(FlowNode block : current.iterateEnclosingBlocks()) {
                if("Allocate node : Start".equalsIgnoreCase(block.getDisplayName())){
                    return block;
                }
            }
            return null;
        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("DatadogStepListener.findFirstAllocateNodeStart", start, end);
        }
    }
}
