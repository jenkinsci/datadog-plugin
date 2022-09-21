package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.cleanUpTraceActions;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.statusFromResult;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.toJson;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;
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

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.audit.DatadogAudit;
import org.datadog.jenkins.plugins.datadog.clients.DatadogHttpClient;
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Keeps the logic to send webhooks related to inner jobs of Jenkins Pipelines (datadog levels: stage and job).
 * The top-level job (datadog level: pipeline) is handled by DatadogWebhookBuildLogic
 */
public class DatadogWebhookPipelineLogic {

    private static final String CI_PROVIDER = "jenkins";
    private static final String HOSTNAME_NONE = "none";
    private static final Logger logger = Logger.getLogger(DatadogWebhookPipelineLogic.class.getName());

    private final DatadogHttpClient client;

    public DatadogWebhookPipelineLogic(final DatadogHttpClient client) {
        this.client = client;
    }

    public void execute(Run run, FlowNode flowNode) {

        if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
            return;
        }

        final IsPipelineAction isPipelineAction = run.getAction(IsPipelineAction.class);
        if(isPipelineAction == null) {
            run.addAction(new IsPipelineAction());
        }

        final BuildWebhookAction buildWebhookAction = run.getAction(BuildWebhookAction.class);
        if(buildWebhookAction == null) {
            return;
        }

        final BuildData buildData = buildWebhookAction.getBuildData();
        if(!isLastNode(flowNode)){
            final BuildPipelineNode pipelineNode = buildPipelineNode(flowNode);
            updateBuildData(buildData, run, pipelineNode, flowNode);
            updateCIGlobalTags(run);
            return;
        }

        final FlowEndNode flowEndNode = (FlowEndNode) flowNode;
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

        final BuildPipelineNode root = pipeline.buildTree();
        collectTraces(run, buildData, root);

        // Explicit removal of InvisibleActions used to collect Traces when the Run finishes.
        cleanUpTraceActions(run);
    }

    private void updateBuildData(BuildData buildData, Run<?, ?> run, BuildPipelineNode pipelineNode, FlowNode node) {
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

    private void collectTraces(final Run run, final BuildData buildData, final BuildPipelineNode current) {

        if(!isTraceable(current)) {
            // If the current node is not traceable, we continue with its children
            for(final BuildPipelineNode child : current.getChildren()) {
                collectTraces(run, buildData, child);
            }
            return;
        }

        // If the root has propagated queue time, we need to adjust all startTime and endTime from Jenkins pipelines
        // because this time will be subtracted in the root. See DatadogTraceBuildLogic#finishBuildTrace method.
        final long propagatedMillisInQueue = Math.max(buildData.getPropagatedMillisInQueue(-1L), 0);
        final long fixedStartTimeMillis = TimeUnit.MICROSECONDS.toMillis(current.getStartTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue));
        final long fixedEndTimeMillis = TimeUnit.MICROSECONDS.toMillis(current.getEndTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue));
        final String jenkinsResult = getResult(current);
        final String status = statusFromResult(jenkinsResult);
        final String prefix = current.getType().getTagName();
        final String buildLevel = current.getType().getBuildLevel();

        final Map<String, String> envVars = current.getEnvVars();

        JSONObject payload = new JSONObject();
        payload.put("level", buildLevel);
        final String url = envVars.get("BUILD_URL") != null ? envVars.get("BUILD_URL") : buildData.getBuildUrl("");
        if(StringUtils.isNotBlank(url)) {
            payload.put("url", url + "execution/node/"+current.getId()+"/");
        }
        payload.put("start", DatadogUtilities.toISO8601(new Date(fixedStartTimeMillis)));
        payload.put("end", DatadogUtilities.toISO8601(new Date(fixedEndTimeMillis)));
        payload.put("partial_retry", false);
        payload.put("queue_time", TimeUnit.NANOSECONDS.toMillis(getNanosInQueue(current)));
        payload.put("status", status);

        payload.put("id", current.getId());
        payload.put("name", current.getName());

        payload.put("pipeline_unique_id", buildData.getBuildTag(""));
        payload.put("pipeline_name", buildData.getBaseJobName(""));
        if (buildLevel.equals("job")) {
             payload.put("stage_id", current.getStageId());
             payload.put("stage_name", current.getStageName());
        }

        // Errors
        if(current.isError() && current.getErrorObj() != null) {

            JSONObject errPayload = new JSONObject();

            final Throwable error = current.getErrorObj();
            errPayload.put("message", error.getMessage());
            errPayload.put("type", error.getClass().getName());
            errPayload.put("domain", "unknown");
            final StringWriter errorString = new StringWriter();
            error.printStackTrace(new PrintWriter(errorString));
            errPayload.put("stack", errorString.toString());

            payload.put("error", errPayload);
        }

        // Node
        {
            JSONObject nodePayload = new JSONObject();

            final String nodeName = getNodeName(run, current, buildData);
            nodePayload.put("name", nodeName);

            if(!"master".equalsIgnoreCase(nodeName)){
                final String workerHostname = getNodeHostname(run, current);
                // If the worker hostname is equals to controller hostname but the node name is not "master"
                // then we could not detect the worker hostname properly. We set _dd.hostname to 'none' explicitly.
                if(buildData.getHostname("").equalsIgnoreCase(workerHostname)) {
                    nodePayload.put("hostname", HOSTNAME_NONE);
                } else {
                    nodePayload.put("hostname", (workerHostname != null) ? workerHostname : HOSTNAME_NONE);
                }
            } else {
                nodePayload.put("hostname", DatadogUtilities.getHostname(null));
            }

            final String workspace = current.getWorkspace() != null ? current.getWorkspace() : buildData.getWorkspace("");
            nodePayload.put("workspace", workspace);

            final Set<String> nodeLabels = getNodeLabels(run, current, nodeName);
            nodePayload.put("labels", JSONArray.fromObject((nodeLabels)));

            payload.put("node", nodePayload);
        }

        // Git info
        {
            JSONObject gitPayload = new JSONObject();

            final String rawGitBranch = GitUtils.resolveGitBranch(envVars, buildData);
            String gitBranch = null;
            String gitTag = null;
            if(rawGitBranch != null && !rawGitBranch.isEmpty()) {
                gitBranch = normalizeBranch(rawGitBranch);
                if(gitBranch != null) {
                    gitPayload.put("branch", gitBranch);
                }
                gitTag = normalizeTag(rawGitBranch);
                if(gitTag != null) {
                    gitPayload.put("tag", gitTag);
                }
            }

            // If the user set DD_GIT_TAG manually,
            // we override the git.tag value.
            gitTag = GitUtils.resolveGitTag(envVars, buildData);
            if(StringUtils.isNotEmpty(gitTag)){
                gitPayload.put("tag", gitTag);
            }

            // If we could detect a valid commit, the buildData object will contain that commit.
            // If we could not detect a valid commit, that means that the GIT_COMMIT environment variable
            // was overridden by the user at top level, so we set the content what we have (despite it's not valid).
            // We will show a logger.warning at the end of the pipeline.
            final String gitCommit = GitUtils.resolveGitCommit(envVars, buildData);
            if(gitCommit != null && !gitCommit.isEmpty()) {
                gitPayload.put("sha", gitCommit);
            }

            final String gitRepoUrl = GitUtils.resolveGitRepositoryUrl(envVars, buildData);
            if (gitRepoUrl != null && !gitRepoUrl.isEmpty()) {
                gitPayload.put("repository_url", filterSensitiveInfo(gitRepoUrl));
            }

            final String gitMessage = buildData.getGitMessage("");
            if (gitMessage != null && !gitMessage.isEmpty()) {
                gitPayload.put("message", gitMessage);
            }

            final String gitAuthorDate = buildData.getGitAuthorDate("");
            if (gitAuthorDate != null && !gitAuthorDate.isEmpty()) {
                gitPayload.put("author_time", gitAuthorDate);
            }

            final String gitCommitDate = buildData.getGitCommitterDate("");
            if (gitCommitDate != null && !gitCommitDate.isEmpty()) {
                gitPayload.put("commit_time", gitCommitDate);
            }

            final String gitCommitterName = buildData.getGitCommitterName("");
            if (gitCommitterName != null && !gitCommitterName.isEmpty()) {
                gitPayload.put("committer_name", gitCommitterName);
            }

            final String gitCommitterEmail = buildData.getGitCommitterEmail("");
            if (gitCommitterEmail != null && !gitCommitterEmail.isEmpty()) {
                gitPayload.put("committer_email", gitCommitterEmail);
            }

            final String gitAuthorName = buildData.getGitAuthorName("");
            if (gitAuthorName != null && !gitAuthorName.isEmpty()) {
                gitPayload.put("author_name", gitAuthorName);
            }

            final String gitAuthorEmail = buildData.getGitAuthorEmail("");
            if (gitAuthorEmail != null && !gitAuthorEmail.isEmpty()) {
                gitPayload.put("author_email", gitAuthorEmail);
            }

            final String gitDefaultBranch = buildData.getGitDefaultBranch("");
            if (gitDefaultBranch != null && !gitDefaultBranch.isEmpty()) {
                gitPayload.put("default_branch", gitDefaultBranch);
            }

            if (gitPayload.keySet().containsAll(GitUtils.WEBHOOK_REQUIRED_GIT_KEYS)) {
                payload.put("git", gitPayload);
            }
        }

        // Tags
        // Here we include both global tags and fields that are not supported as regular fields by the webhooks intake
        {
            JSONArray tagsPayload = new JSONArray();

            final CIGlobalTagsAction ciGlobalTagsAction = run.getAction(CIGlobalTagsAction.class);
            if(ciGlobalTagsAction != null) {
                final Map<String, String> globalTags = ciGlobalTagsAction.getTags();
                for(Map.Entry<String, String> globalTagEntry : globalTags.entrySet()) {
                    tagsPayload.add(globalTagEntry.getKey() + ":" + globalTagEntry.getValue());
                }

            }

            // Jenkins specific
            tagsPayload.add(CITags._DD_CI_INTERNAL + ":" + current.isInternal());
            if (StringUtils.isNotEmpty(jenkinsResult)) {
                tagsPayload.add(CITags.JENKINS_RESULT + ":" + jenkinsResult.toLowerCase());
            }

            // For backwards compat
            tagsPayload.add(prefix + CITags._RESULT + ":" + status);

            // User
            final String user = envVars.get("USER") != null ? envVars.get("USER") : buildData.getUserId();
            tagsPayload.add("username" + ":" + user);

            // Pipeline Parameters
            if(!buildData.getBuildParameters().isEmpty()) {
                tagsPayload.add("parameters" + ":" + toJson(buildData.getBuildParameters()));
            }

            // Arguments
            final String nodePrefix = current.getType().name().toLowerCase();
            for(Map.Entry<String, Object> entry : current.getArgs().entrySet()) {
                tagsPayload.add(CI_PROVIDER + "." + nodePrefix + ".args."+entry.getKey() + ":" + String.valueOf(entry.getValue()));
                if("script".equals(entry.getKey())){
                    tagsPayload.add(prefix + ".script" + ":" + String.valueOf(entry.getValue()));
                }
            }

            payload.put("tags", tagsPayload);
        }

        for(final BuildPipelineNode child : current.getChildren()) {
            collectTraces(run, buildData, child);
        }

        client.postWebhook(payload.toString());
    }

    private Long getNanosInQueue(BuildPipelineNode current) {
        // If the concrete queue time for this node is not set
        // we look for the queue time propagated by its children.
        return Math.max(Math.max(current.getNanosInQueue(), current.getPropagatedNanosInQueue()), 0);
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private Set<String> getNodeLabels(Run run, BuildPipelineNode current, String nodeName) {
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

    private String getResult(BuildPipelineNode current) {
        return (current.getPropagatedResult() != null) ? current.getPropagatedResult() : current.getResult();
    }

    private String getNodeName(Run<?, ?> run, BuildPipelineNode current, BuildData buildData) {
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

    private String getNodeHostname(Run<?, ?> run, BuildPipelineNode current) {
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

    private boolean isTraceable(BuildPipelineNode node) {
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
    private boolean isLastNode(FlowNode flowNode) {
        return flowNode instanceof FlowEndNode;
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

    private BuildPipelineNode buildPipelineNode(FlowNode flowNode) {
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

    private void updateCIGlobalTags(Run run) {
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
}
