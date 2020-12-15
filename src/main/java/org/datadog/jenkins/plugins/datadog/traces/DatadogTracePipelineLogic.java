package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getNormalizedResultForTraces;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;

import datadog.trace.api.DDId;
import datadog.trace.api.DDTags;
import datadog.trace.core.DDSpan;
import datadog.trace.core.DDSpanContext;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipeline;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.datadog.jenkins.plugins.datadog.model.StageBreakdownAction;
import org.datadog.jenkins.plugins.datadog.model.StageData;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Keeps the logic to send traces related to Jenkins Pipelines.
 */
public class DatadogTracePipelineLogic {

    private static final String CI_PROVIDER = "jenkins";
    private static final String HOSTNAME_NONE = "none";
    private static final Logger logger = Logger.getLogger(DatadogTracePipelineLogic.class.getName());

    private static Field ddSpanField;
    private static Field ddSpanIdField;

    static {
        // As there is no public API to set the spanID manually,
        // we'll use reflection to do it.
        // See substituteSpanId(...) and sendTrace(...) methods.
        try {
            ddSpanField = Class.forName("datadog.opentracing.OTSpan", true, DatadogTracePipelineLogic.class.getClassLoader()).getDeclaredField("delegate");
            ddSpanField.setAccessible(true);
        } catch (Exception e){
            ddSpanField = null;
            logger.fine("Unable to find the DDSpan.delegate field. Error: " + e.getMessage());
        }

        try {
            ddSpanIdField = DDSpanContext.class.getDeclaredField("spanId");
            ddSpanIdField.setAccessible(true);
        } catch (Exception e) {
            ddSpanIdField = null;
            logger.fine("Unable to find the DDSpanContext.spanId field. Error: " + e.getMessage());
        }

    }

    private final Tracer tracer;

    public DatadogTracePipelineLogic(Tracer tracer) {
        this.tracer = tracer;
    }

    public void execute(Run run, FlowNode flowNode) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildTraces()) {
            return;
        }

        if(this.tracer == null) {
            logger.severe("Unable to send pipeline traces. Tracer is null");
            return;
        }

        final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
        if(buildSpanAction == null) {
            return;
        }

        final BuildData buildData = buildSpanAction.getBuildData();
        if(!isLastNode(flowNode)){
            final BuildPipelineNode pipelineNode = buildPipelineNode(flowNode);
            updateStageBreakdown(run, pipelineNode);
            updateBuildData(buildData, run, pipelineNode, flowNode);
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

        final SpanContext spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new BuildTextMapAdapter(buildSpanAction.getBuildSpanPropatation()));
        final BuildPipelineNode root = pipeline.buildTree();
        try {
            sendTrace(tracer, buildData, root, spanContext);
        } catch (Exception e){
            logger.severe("Unable to send traces. Exception:" + e);
        }
    }

    private void updateBuildData(BuildData buildData, Run<?, ?> run, BuildPipelineNode pipelineNode, FlowNode node) {
        if(pipelineNode == null){
            return;
        }

        final String gitBranch = pipelineNode.getEnvVars().get("GIT_BRANCH");
        if(gitBranch != null && buildData.getBranch("").isEmpty()) {
            buildData.setBranch(gitBranch);
        }

        final String gitUrl = pipelineNode.getEnvVars().get("GIT_URL");
        if(gitUrl != null && buildData.getGitUrl("").isEmpty()) {
            buildData.setGitUrl(gitUrl);
        }

        final String gitCommit = pipelineNode.getEnvVars().get("GIT_COMMIT");
        if(gitCommit != null && buildData.getGitCommit("").isEmpty()) {
            buildData.setGitCommit(gitCommit);
        }

        final GitCommitAction commitAction = buildGitCommitAction(run,pipelineNode, node);
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

        final GitRepositoryAction repositoryAction = buildGitRepositoryAction(run, pipelineNode, node);
        if(repositoryAction != null) {
            if(buildData.getGitDefaultBranch("").isEmpty()) {
                buildData.setGitDefaultBranch(repositoryAction.getDefaultBranch());
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
    }


    private void sendTrace(final Tracer tracer, final BuildData buildData, final BuildPipelineNode current, final SpanContext parentSpanContext) {
        if(!isTraceable(current)){
            logger.severe("Node " + current.getName() + " is not traceable.");
            return;
        }

        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(buildOperationName(current)).withStartTimestamp(current.getStartTimeMicros());

        if(parentSpanContext != null) {
            spanBuilder.asChildOf(parentSpanContext);
        }

        spanBuilder
                .withTag(DDTags.SERVICE_NAME, DatadogUtilities.getDatadogGlobalDescriptor().getTraceServiceName())
                .withTag(DDTags.RESOURCE_NAME, current.getName())
                .withTag(DDTags.SPAN_TYPE, "ci")
                .withTag(DDTags.LANGUAGE_TAG_KEY, "");

        final Map<String, Object> traceTags = buildTraceTags(current, buildData);
        for(Map.Entry<String, Object> traceTag : traceTags.entrySet()) {
            if(traceTag.getValue() instanceof Number) {
                spanBuilder.withTag(traceTag.getKey(), (Number) traceTag.getValue());
            } else if(traceTag.getValue() instanceof Boolean) {
                spanBuilder.withTag(traceTag.getKey(), (Boolean) traceTag.getValue());
            } else {
                spanBuilder.withTag(traceTag.getKey(), String.valueOf(traceTag.getValue()));
            }
        }

        final Span span = spanBuilder.start();
        final DDId generatedSpanId = current.getGeneratedSpanId();

        // If the generated spanID exists, we need to set it
        // in the new created span. This is needed to associate
        // external spans (Datadog CLI Bash) to the Jenkins spans.

        // This generated spanID is available in the Environment Variables
        // of the every workflow Step (see TraceStepEnvironmentContributor class)
        // and it will be potentially used by external tools to correlate
        // external spans with the Jenkins spans.
        if (generatedSpanId != null) {
            substituteSpanId(span, generatedSpanId);
        }

        for(final BuildPipelineNode child : current.getChildren()) {
            sendTrace(tracer, buildData, child, span.context());
        }

        //Logs
        //NOTE: Implement sendNodeLogs

        span.finish(current.getEndTimeMicros());
    }

    private Map<String, Object> buildTraceTags(final BuildPipelineNode current, final BuildData buildData) {
        final String prefix = current.getType().getTagName();
        final String buildLevel = current.getType().getBuildLevel();
        final Map<String, String> envVars = current.getEnvVars();

        final Map<String, Object> tags = new HashMap<>();
        tags.put(CITags.CI_PROVIDER_NAME, CI_PROVIDER);
        tags.put(prefix + CITags._NAME, current.getName());
        tags.put(prefix + CITags._NUMBER, current.getId());
        final String status = getNormalizedResultForTraces(Result.fromString(current.getResult()));
        tags.put(prefix + CITags._RESULT, status);
        tags.put(CITags.STATUS, status);

        final String url = envVars.get("BUILD_URL") != null ? envVars.get("BUILD_URL") : buildData.getBuildUrl("");
        if(StringUtils.isNotBlank(url)) {
            tags.put(prefix + CITags._URL, url + "execution/node/"+current.getId()+"/");
        }

        final String workspace = current.getWorkspace() != null ? current.getWorkspace() : buildData.getWorkspace("");
        tags.put(CITags.WORKSPACE_PATH, workspace);

        tags.put(CITags._DD_CI_INTERNAL, current.isInternal());
        if(!current.isInternal()) {
            tags.put(CITags._DD_CI_BUILD_LEVEL, buildLevel);
        }
        tags.put(CITags.JENKINS_RESULT, current.getResult().toLowerCase());
        tags.put(CITags.ERROR, String.valueOf(current.isError()));

        //Git Info
        final String rawGitBranch = envVars.get("GIT_BRANCH") != null ? envVars.get("GIT_BRANCH") : buildData.getBranch("");
        String gitBranch = null;
        String gitTag = null;
        if(rawGitBranch != null && !rawGitBranch.isEmpty()) {
            gitBranch = normalizeBranch(rawGitBranch);
            if(gitBranch != null) {
                tags.put(CITags.GIT_BRANCH, gitBranch);
            }

            gitTag = normalizeTag(rawGitBranch);
            if(gitTag != null) {
                tags.put(CITags.GIT_TAG, gitTag);
            }
        }

        final String gitCommit = envVars.get("GIT_COMMIT") !=  null ? envVars.get("GIT_COMMIT") : buildData.getGitCommit("");
        if(gitCommit != null && !gitCommit.isEmpty()) {
            tags.put(CITags.GIT_COMMIT__SHA, gitCommit); //Maintain retrocompatibility
            tags.put(CITags.GIT_COMMIT_SHA, gitCommit);
        }

        final String gitRepoUrl = envVars.get("GIT_URL") != null ? envVars.get("GIT_URL") : buildData.getGitUrl("");
        if (gitRepoUrl != null && !gitRepoUrl.isEmpty()) {
            tags.put(CITags.GIT_REPOSITORY_URL, gitRepoUrl);
        }

        // User info
        final String user = envVars.get("USER") != null ? envVars.get("USER") : buildData.getUserId();
        tags.put(CITags.USER_NAME, user);

        //Node info
        if(current.getNodeName() != null) {
            tags.put(CITags.NODE_NAME, current.getNodeName());

            String nodeHostname = HOSTNAME_NONE;
            if(current.getNodeHostname() != null) {
                nodeHostname = current.getNodeHostname();
            } else if(current.getNodeName().equals("master")) {
                // If the nodeName is master,
                // we can set the hostname from the buildData.
                nodeHostname = buildData.getHostname("");
            }

            tags.put(CITags._DD_HOSTNAME, nodeHostname);
        } else {
            // If there is no node explicitly set for the step,
            // we consider that is the node from the build.
            tags.put(CITags.NODE_NAME, buildData.getNodeName(""));
            tags.put(CITags._DD_HOSTNAME, buildData.getHostname(""));
        }

        // Arguments
        final String nodePrefix = current.getType().name().toLowerCase();
        for(Map.Entry<String, Object> entry : current.getArgs().entrySet()) {
            tags.put(CI_PROVIDER + "." + nodePrefix + ".args."+entry.getKey(), String.valueOf(entry.getValue()));

            if("script".equals(entry.getKey())){
                tags.put(prefix + ".script", String.valueOf(entry.getValue()));
            }
        }

        // Errors
        if(current.isError() && current.getErrorObj() != null){
            final Throwable error = current.getErrorObj();
            tags.put(DDTags.ERROR_MSG, error.getMessage());
            tags.put(DDTags.ERROR_TYPE, error.getClass().getName());

            final StringWriter errorString = new StringWriter();
            error.printStackTrace(new PrintWriter(errorString));
            tags.put(DDTags.ERROR_STACK, errorString.toString());
        }

        // Propagate Pipeline Name
        final JobNameWrapper jobNameWrapper = new JobNameWrapper(buildData.getJobName(""), gitBranch != null ? gitBranch : gitTag);
        tags.put(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME, jobNameWrapper.getTraceJobName());
        tags.put(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID, buildData.getBuildTag(""));

        // Propagate Stage Name
        if(!BuildPipelineNode.NodeType.STAGE.equals(current.getType()) && current.getStageName() != null) {
            tags.put(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME, current.getStageName());
        }

        return tags;
    }

    private String buildOperationName(BuildPipelineNode current) {
        return CI_PROVIDER + "." + current.getType().name().toLowerCase() + ((current.isInternal()) ? ".internal" : "");
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

    /**
     * Substitute the current spanID by the generated spanID during the Step execution.
     * This is needed to associate the Steps spans with external spans (e.g. Datadog CLI Bash wrapper)
     * @param span
     * @param generatedSpanId
     */
    private void substituteSpanId(final Span span, final DDId generatedSpanId) {
        try {
            if(ddSpanField == null || ddSpanIdField == null) {
                return;
            }

            final DDSpan ddSpan = (DDSpan) ddSpanField.get(span);
            final DDSpanContext spanContext = ddSpan.context();
            ddSpanIdField.set(spanContext, generatedSpanId);
        } catch (Exception e) {
            logger.fine("Unable to substitute the spanId in the span: "+span+". Error: " + e.getMessage());
        }
    }

    private GitCommitAction buildGitCommitAction(Run<?, ?> run, BuildPipelineNode pipelineNode, FlowNode node) {
        try {
            final TaskListener listener = node.getExecution().getOwner().getListener();
            final EnvVars envVars = new EnvVars(pipelineNode.getEnvVars());
            final String gitCommit = pipelineNode.getEnvVars().get("GIT_COMMIT");
            final String nodeName = pipelineNode.getNodeName();
            final String workspace = pipelineNode.getWorkspace();
            return GitUtils.buildGitCommitAction(run, listener, envVars, gitCommit, nodeName, workspace);
        } catch (Exception e) {
            logger.fine("Unable to build GitCommitAction. Error: " + e);
            return null;
        }
    }

    private GitRepositoryAction buildGitRepositoryAction(Run<?, ?> run, BuildPipelineNode pipelineNode, FlowNode node) {
        try {
            final TaskListener listener = node.getExecution().getOwner().getListener();
            final EnvVars envVars = new EnvVars(pipelineNode.getEnvVars());
            final String nodeName = pipelineNode.getNodeName();
            final String workspace = pipelineNode.getWorkspace();
            return GitUtils.buildGitRepositoryAction(run, listener, envVars, nodeName, workspace);
        } catch (Exception e) {
            logger.fine("Unable to build GitRepositoryAction. Error: " + e);
            return null;
        }
    }

    private BuildPipelineNode buildPipelineNode(FlowNode flowNode) {
        BuildPipelineNode pipelineNode = null;
        if(flowNode instanceof BlockEndNode) {
            pipelineNode = new BuildPipelineNode((BlockEndNode) flowNode);
        } else if(flowNode instanceof StepAtomNode) {
            pipelineNode = new BuildPipelineNode((StepAtomNode) flowNode);
        }
        return pipelineNode;
    }

    private void updateStageBreakdown(final Run<?,?> run, BuildPipelineNode pipelineNode) {
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
    }
}
