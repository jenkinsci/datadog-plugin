package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getNormalizedResultForTraces;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.toJson;
import static org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode.NodeType.PIPELINE;
import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;

import datadog.trace.api.DDTags;
import datadog.trace.api.interceptor.MutableSpan;
import hudson.EnvVars;
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
import org.datadog.jenkins.plugins.datadog.model.CIGlobalTagsAction;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.StageBreakdownAction;
import org.datadog.jenkins.plugins.datadog.model.StageData;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Keeps the logic to send traces related to Jenkins Pipelines.
 */
public class DatadogTracePipelineLogic {

    private static final String CI_PROVIDER = "jenkins";
    private static final String HOSTNAME_NONE = "none";
    private static final Logger logger = Logger.getLogger(DatadogTracePipelineLogic.class.getName());

    private final Tracer tracer;

    public DatadogTracePipelineLogic(Tracer tracer) {
        this.tracer = tracer;
    }

    public void execute(Run run, FlowNode flowNode) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isEnabledCiVisibility()) {
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

        final SpanContext spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new BuildTextMapAdapter(buildSpanAction.getBuildSpanPropatation()));
        final BuildPipelineNode root = pipeline.buildTree();

        try {
            sendTrace(tracer, run, buildData, root, spanContext);
        } catch (Exception e){
            logger.severe("Unable to send traces. Exception:" + e);
        }
    }

    private void updateBuildData(BuildData buildData, Run<?, ?> run, BuildPipelineNode pipelineNode, FlowNode node) {
        if(pipelineNode == null){
            return;
        }

        buildData.setPropagatedMillisInQueue(TimeUnit.NANOSECONDS.toMillis(getNanosInQueue(pipelineNode)));

        final String gitBranch = pipelineNode.getEnvVars().get("GIT_BRANCH");
        if(gitBranch != null && buildData.getBranch("").isEmpty()) {
            buildData.setBranch(gitBranch);
        }

        final String gitUrl = pipelineNode.getEnvVars().get("GIT_URL");
        if(gitUrl != null && buildData.getGitUrl("").isEmpty()) {
            buildData.setGitUrl(gitUrl);
        }

        final String gitCommit = pipelineNode.getEnvVars().get("GIT_COMMIT");
        final String buildDataGitCommit = buildData.getGitCommit("");
        if(gitCommit != null && (buildDataGitCommit.isEmpty() || !isValidCommit(buildDataGitCommit))){
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

    private void sendTrace(final Tracer tracer, final Run run, final BuildData buildData, final BuildPipelineNode current, final SpanContext parentSpanContext) {
        if(!isTraceable(current)){
            // If the current node is not traceable, we continue with its children
            for(final BuildPipelineNode child : current.getChildren()) {
                sendTrace(tracer, run, buildData, child, parentSpanContext);
            }
            return;
        }

        // If the root span has propagated queue time, we need to adjust all startTime and endTime from Jenkins pipelines spans
        // because this time will be subtracted in the root span. See DatadogTraceBuildLogic#finishBuildTrace method.
        final long propagatedMillisInQueue = Math.max(buildData.getPropagatedMillisInQueue(-1L), 0);
        final long fixedStartTimeMicros = current.getStartTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue);
        final long fixedEndTimeMicros = current.getEndTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue);

        // At this point, the current node is traceable.
        final Tracer.SpanBuilder spanBuilder = tracer
                .buildSpan(buildOperationName(current))
                // Queue time for the indexed spans is reported on its child spans.
                // We need to remove the queue time from the span. In this case, we're adding it to the
                // fixed startTime to avoid having the queue time in the span duration.
                .withStartTimestamp(fixedStartTimeMicros + TimeUnit.NANOSECONDS.toMicros(getNanosInQueue(current)));
        if(parentSpanContext != null) {
            spanBuilder.asChildOf(parentSpanContext);
        }

        spanBuilder
                .withTag(DDTags.SERVICE_NAME, DatadogUtilities.getDatadogGlobalDescriptor().getCiInstanceName())
                .withTag(DDTags.RESOURCE_NAME, current.getName())
                .withTag(DDTags.SPAN_TYPE, "ci")
                .withTag(DDTags.LANGUAGE_TAG_KEY, "");

        final Map<String, Object> traceTags = buildTraceTags(run, current, buildData);

        // Set tags
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

        try {
            // Set metrics
            if(span instanceof MutableSpan) {
                final MutableSpan mutableSpan = (MutableSpan) span;
                final Map<String, Long> traceMetrics = buildTraceMetrics(current);
                for(Map.Entry<String, Long> traceMetric : traceMetrics.entrySet()) {
                    if(traceMetric.getValue() != null) {
                        mutableSpan.setMetric(traceMetric.getKey(), traceMetric.getValue());
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Unable to set metrics in the span, exception: " + e);
        }

        for(final BuildPipelineNode child : current.getChildren()) {
            sendTrace(tracer, run, buildData, child, span.context());
        }

        //Logs
        //NOTE: Implement sendNodeLogs

        span.finish(fixedEndTimeMicros);
    }

    private Long getNanosInQueue(BuildPipelineNode current) {
        // If the concrete queue time for this node is not set
        // we look for the queue time propagated by its children.
        return Math.max(Math.max(current.getNanosInQueue(), current.getPropagatedNanosInQueue()), 0);
    }

    private Map<String, Long> buildTraceMetrics(BuildPipelineNode current) {
        final Map<String, Long> metrics = new HashMap<>();
        metrics.put(CITags.QUEUE_TIME, TimeUnit.NANOSECONDS.toSeconds(getNanosInQueue(current)));
        return metrics;
    }

    private Map<String, Object> buildTraceTags(final Run run, final BuildPipelineNode current, final BuildData buildData) {
        final String prefix = current.getType().getTagName();
        final String buildLevel = current.getType().getBuildLevel();
        final Map<String, String> envVars = current.getEnvVars();

        final Map<String, Object> tags = new HashMap<>();
        tags.put(CITags.CI_PROVIDER_NAME, CI_PROVIDER);
        tags.put(CITags._DD_ORIGIN, ORIGIN_CIAPP_PIPELINE);
        tags.put(prefix + CITags._NAME, current.getName());
        tags.put(prefix + CITags._NUMBER, current.getId());
        final String status = getNormalizedResultForTraces(getResult(current));
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
            tags.put(CITags._DD_CI_LEVEL, buildLevel);
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

        // If we could detect a valid commit, the buildData object will contain that commit.
        // If we could not detect a valid commit, that means that the GIT_COMMIT environment variable
        // was overridden by the user at top level, so we set the content what we have (despite it's not valid).
        // We will show a logger.warning at the end of the pipeline.
        final String envGitCommit = envVars.get("GIT_COMMIT");
        final String gitCommit = (isValidCommit(envGitCommit)) ? envGitCommit : buildData.getGitCommit("");
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

        // Node info
        final String nodeName = getNodeName(run, current, buildData);
        tags.put(CITags.NODE_NAME, nodeName);

        final String nodeLabels = toJson(getNodeLabels(run, current, nodeName));
        if(!nodeLabels.isEmpty()){
            tags.put(CITags.NODE_LABELS, nodeLabels);
        }

        // If the NodeName == "master", we don't set _dd.hostname. It will be overridden by the Datadog Agent. (Traces are only available using Datadog Agent)
        // If the NodeName != "master", we set _dd.hostname to 'none' explicitly, cause we cannot calculate the worker hostname.
        if(!"master".equalsIgnoreCase(nodeName)){
            tags.put(CITags._DD_HOSTNAME, HOSTNAME_NONE);
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
        tags.put(PIPELINE.getTagName() + CITags._NAME, jobNameWrapper.getTraceJobName());
        tags.put(PIPELINE.getTagName() + CITags._ID, buildData.getBuildTag(""));

        // Propagate Stage Name
        if(!BuildPipelineNode.NodeType.STAGE.equals(current.getType()) && current.getStageName() != null) {
            tags.put(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME, current.getStageName());
        }

        // CI Tags propagation
        final CIGlobalTagsAction ciGlobalTagsAction = run.getAction(CIGlobalTagsAction.class);
        if(ciGlobalTagsAction != null) {
            final Map<String, String> globalTags = ciGlobalTagsAction.getTags();
            for(Map.Entry<String, String> globalTagEntry : globalTags.entrySet()) {
                tags.put(globalTagEntry.getKey(), globalTagEntry.getValue());
            }
        }

        return tags;
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

    private GitCommitAction buildGitCommitAction(Run<?, ?> run, BuildPipelineNode pipelineNode, FlowNode node) {
        try {
            final TaskListener listener = node.getExecution().getOwner().getListener();
            final EnvVars envVars = new EnvVars(pipelineNode.getEnvVars());
            final String gitCommit = pipelineNode.getEnvVars().get("GIT_COMMIT");
            if(!isValidCommit(gitCommit)) {
                return null;
            }

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

    private void updateCIGlobalTags(Run run) {
        final CIGlobalTagsAction ciGlobalTagsAction = run.getAction(CIGlobalTagsAction.class);
        if(ciGlobalTagsAction == null) {
            return;
        }

        final Map<String, String> tags = TagsUtil.convertTagsToMapSingleValues(DatadogUtilities.getTagsFromPipelineAction(run));
        ciGlobalTagsAction.putAll(tags);
    }
}
