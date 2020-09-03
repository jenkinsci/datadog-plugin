package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getNormalizedResultForTraces;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;

import datadog.trace.api.DDTags;
import hudson.model.Result;
import hudson.model.Run;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipeline;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Keeps the logic to send traces related to Jenkins Pipelines.
 */
public class DatadogTracePipelineLogic {

    private static final String CI_PROVIDER = "jenkins";
    private static final Logger logger = Logger.getLogger(DatadogTracePipelineLogic.class.getName());

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
            updateBuildData(buildData, flowNode);
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
        sendTrace(tracer, buildData, root, spanContext);
    }

    private void updateBuildData(BuildData buildData, FlowNode node) {
        BuildPipelineNode pipelineNode = null;
        if(node instanceof BlockEndNode) {
            pipelineNode = new BuildPipelineNode((BlockEndNode) node);
        } else if(node instanceof StepAtomNode) {
            pipelineNode = new BuildPipelineNode((StepAtomNode) node);
        }

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

        for(final BuildPipelineNode child : current.getChildren()) {
            sendTrace(tracer, buildData, child, span.context());
        }

        //Logs
        //NOTE: Implement sendNodeLogs

        span.finish(current.getEndTimeMicros());
    }


    private Map<String, Object> buildTraceTags(final BuildPipelineNode current, final BuildData buildData) {
        final String prefix = current.getType().getTagName();
        final Map<String, String> envVars = current.getEnvVars();

        final Map<String, Object> tags = new HashMap<>();
        tags.put(CITags.CI_PROVIDER_NAME, CI_PROVIDER);
        tags.put(prefix + CITags._NAME, current.getName());
        tags.put(prefix + CITags._NUMBER, current.getId());
        tags.put(prefix + CITags._RESULT, getNormalizedResultForTraces(Result.fromString(current.getResult())));

        final String url = envVars.get("BUILD_URL") != null ? envVars.get("BUILD_URL") : buildData.getBuildUrl("");
        if(StringUtils.isNotBlank(url)) {
            tags.put(prefix + CITags._URL, url + "execution/node/"+current.getId()+"/");
        }

        final String workspace = current.getWorkspace() != null ? current.getWorkspace() : buildData.getWorkspace("");
        tags.put(CITags.WORKSPACE_PATH, workspace);

        tags.put(CITags._DD_CI_INTERNAL, current.isInternal());
        tags.put(CITags.JENKINS_RESULT, current.getResult().toLowerCase());
        tags.put(CITags.ERROR, String.valueOf(current.isError()));

        //Git Info
        final String rawGitBranch = envVars.get("GIT_BRANCH") != null ? envVars.get("GIT_BRANCH") : buildData.getBranch("");
        if(rawGitBranch != null && !rawGitBranch.isEmpty()) {
            final String gitBranch = normalizeBranch(rawGitBranch);
            if(gitBranch != null) {
                tags.put(CITags.GIT_BRANCH, gitBranch);
            }

            final String gitTag = normalizeTag(rawGitBranch);
            if(gitTag != null) {
                tags.put(CITags.GIT_TAG, gitTag);
            }
        }

        final String gitCommit = envVars.get("GIT_COMMIT") !=  null ? envVars.get("GIT_COMMIT") : buildData.getGitCommit("");
        if(gitCommit != null && !gitCommit.isEmpty()) {
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
        final String nodeName = current.getNodeName() != null ? current.getNodeName() : buildData.getNodeName("");
        tags.put(CITags.NODE_NAME, nodeName);

        final String nodeHostname = current.getNodeHostname() != null ? current.getNodeHostname() : buildData.getHostname("");
        tags.put(CITags._DD_HOSTNAME, nodeHostname);

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
}
