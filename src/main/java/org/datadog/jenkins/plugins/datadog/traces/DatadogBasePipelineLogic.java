package org.datadog.jenkins.plugins.datadog.traces;

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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.audit.DatadogAudit;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipeline;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.CIGlobalTagsAction;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Base class with shared code for DatadogTracePipelineLogic and DatadogWebhookPipelineLogic
 */
public class DatadogBasePipelineLogic {

    protected static final String CI_PROVIDER = "jenkins";
    protected static final String HOSTNAME_NONE = "none";
    private static final Logger logger = Logger.getLogger(DatadogBasePipelineLogic.class.getName());

    protected BuildPipelineNode buildPipelineTree(FlowEndNode flowEndNode) {

        final BuildPipeline pipeline = new BuildPipeline();

        // As this logic is evaluated in the last node of the graph,
        // getCurrentHeads() method returns all nodes as a plain list.
        final List<FlowNode> currentHeads = flowEndNode.getExecution().getCurrentHeads();

        // Provided that plain list of nodes, the DepthFirstScanner algorithm
        // is used to visit efficiently every node in form of a DAG.
        final DepthFirstScanner scanner = new DepthFirstScanner();
        scanner.setup(currentHeads);

        // Every found flow node of the DAG is added to the BuildPipeline instance.
        scanner.forEach(pipeline::add);

        return pipeline.buildTree(); // returns the root node
    }

    protected void updateBuildData(BuildData buildData, Run<?, ?> run, BuildPipelineNode pipelineNode, FlowNode node) {
        long start = System.currentTimeMillis();
        try {
            if(pipelineNode == null){
                return;
            }

            buildData.setPropagatedMillisInQueue(TimeUnit.NANOSECONDS.toMillis(getNanosInQueue(pipelineNode)));

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

    protected Long getNanosInQueue(BuildPipelineNode current) {
        // If the concrete queue time for this node is not set
        // we look for the queue time propagated by its children.
        return Math.max(Math.max(current.getNanosInQueue(), current.getPropagatedNanosInQueue()), 0);
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    protected Set<String> getNodeLabels(Run run, BuildPipelineNode current, String nodeName) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
        if (current.getPropagatedNodeLabels() != null && !current.getPropagatedNodeLabels().isEmpty()) {
            return current.getPropagatedNodeLabels();
        } else if (current.getNodeLabels() != null && !current.getNodeLabels().isEmpty()) {
            return current.getNodeLabels();
        } else if (pipelineNodeInfoAction != null && !pipelineNodeInfoAction.getNodeLabels().isEmpty()) {
            return pipelineNodeInfoAction.getNodeLabels();
        }

        if (run.getExecutor() != null && run.getExecutor().getOwner() != null) {
            Set<String> nodeLabels = DatadogUtilities.getNodeLabels(run.getExecutor().getOwner());
            if (nodeLabels != null && !nodeLabels.isEmpty()) {
                return nodeLabels;
            }
        }

        // If there is no labels and the node name is master,
        // we force the label "master".
        if ("master".equalsIgnoreCase(nodeName)) {
            final Set<String> masterLabels = new HashSet<>();
            masterLabels.add("master");
            return masterLabels;
        }

        return Collections.emptySet();
    }

    protected String getResult(BuildPipelineNode current) {
        return (current.getPropagatedResult() != null) ? current.getPropagatedResult() : current.getResult();
    }

    protected String getNodeName(Run<?, ?> run, BuildPipelineNode current, BuildData buildData) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);

        if(current.getPropagatedNodeName() != null) {
            return current.getPropagatedNodeName();
        } else if(current.getNodeName() != null) {
            return current.getNodeName();
        } else if (pipelineNodeInfoAction != null) {
            return pipelineNodeInfoAction.getNodeName();
        }

        return buildData.getNodeName("");
    }

    protected String getNodeHostname(Run<?, ?> run, BuildPipelineNode current) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
        if(current.getPropagatedNodeHostname() != null) {
            return current.getPropagatedNodeHostname();
        } else if(current.getNodeHostname() != null) {
            return current.getNodeHostname();
        } else if (pipelineNodeInfoAction != null) {
            return pipelineNodeInfoAction.getNodeHostname();
        }
        return null;
    }

    protected boolean isTraceable(BuildPipelineNode node) {
        if (node.getStartTimeMicros() == -1L) {
            logger.severe("Unable to send trace of node: " + node.getName() + ". Start Time is not set");
            return false;
        }

        if(node.getEndTimeMicros() == -1L) {
            logger.severe("Unable to send trace of node: " + node.getName() + ". End Time is not set");
            return false;
        }

        if(node.isInternal()){
            logger.fine("Node: " + node.getName() + " is Jenkins internal. We skip it.");
            return false;
        }

        return true;
    }

    /**
     * Check if flowNode is the last node of the pipeline.
     * @param flowNode
     * @return true if flowNode is the last node of the pipeline
     */
    protected boolean isLastNode(FlowNode flowNode) {
        return flowNode instanceof FlowEndNode;
    }

    protected GitCommitAction buildGitCommitAction(Run<?, ?> run, GitClient gitClient, BuildPipelineNode pipelineNode) {
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

    protected GitRepositoryAction buildGitRepositoryAction(Run<?, ?> run, GitClient gitClient, BuildPipelineNode pipelineNode) {
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

    protected BuildPipelineNode buildPipelineNode(FlowNode flowNode) {
        long start = System.currentTimeMillis();
        try {
            BuildPipelineNode pipelineNode = null;
            if(flowNode instanceof BlockEndNode) {
                pipelineNode = new BuildPipelineNode((BlockEndNode) flowNode);
            } else if(flowNode instanceof StepAtomNode) {
                pipelineNode = new BuildPipelineNode((StepAtomNode) flowNode);
            }
            return pipelineNode;
        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("DatadogTracePipelineLogic.buildPipelineNode", start, end);
        }
    }

    protected void updateCIGlobalTags(Run run) {
        long start = System.currentTimeMillis();
        try {
            final CIGlobalTagsAction ciGlobalTagsAction = run.getAction(CIGlobalTagsAction.class);
            if(ciGlobalTagsAction == null) {
                return;
            }

            final Map<String, String> tags = TagsUtil.convertTagsToMapSingleValues(DatadogUtilities.getTagsFromPipelineAction(run));
            ciGlobalTagsAction.putAll(tags);
        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("DatadogTracePipelineLogic.updateCIGlobalTags", start, end);
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
    protected GitClient getGitClient(final Run<?, ?> run, final BuildPipelineNode pipelineNode, final FlowNode node, final String gitUrl, final String gitCommit) {
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
}
