package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.cleanUpTraceActions;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getNormalizedResultForTraces;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.toJson;
import static org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode.NodeType.PIPELINE;
import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.audit.DatadogAudit;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.CIGlobalTagsAction;
import org.datadog.jenkins.plugins.datadog.model.StageBreakdownAction;
import org.datadog.jenkins.plugins.datadog.model.StageData;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.transport.HttpClient;
import org.datadog.jenkins.plugins.datadog.transport.PayloadMessage;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.model.Run;

/**
 * Keeps the logic to send traces related to Jenkins Pipelines.
 */
public class DatadogTracePipelineLogic extends DatadogBasePipelineLogic {

    private static final Logger logger = Logger.getLogger(DatadogTracePipelineLogic.class.getName());

    private final HttpClient agentHttpClient;

    public DatadogTracePipelineLogic(HttpClient agentHttpClient) {
        this.agentHttpClient = agentHttpClient;
    }

    public void execute(Run run, FlowNode flowNode) {

        if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
            return;
        }

        if(this.agentHttpClient == null) {
            logger.severe("Unable to send pipeline traces. Tracer is null");
            return;
        }

        final IsPipelineAction isPipelineAction = run.getAction(IsPipelineAction.class);
        if(isPipelineAction == null) {
            run.addAction(new IsPipelineAction());
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

        final TraceSpan.TraceSpanContext traceSpanContext = buildSpanAction.getBuildSpanContext();
        final BuildPipelineNode root = buildPipelineTree((FlowEndNode) flowNode);

        final List<PayloadMessage> spanBuffer = new ArrayList<>();
        collectTraces(run, spanBuffer, buildData, root, traceSpanContext);

        try {
            if(!spanBuffer.isEmpty()) {
                this.agentHttpClient.send(spanBuffer);
            }
        } catch (Exception e){
            logger.severe("Unable to send traces. Exception:" + e);
        } finally {
            // Explicit removal of InvisibleActions used to collect Traces when the Run finishes.
            cleanUpTraceActions(run);
        }
    }

    private void collectTraces(final Run run, final List<PayloadMessage> spanBuffer, final BuildData buildData, final BuildPipelineNode current, final TraceSpan.TraceSpanContext parentSpanContext) {
        if(!isTraceable(current)) {
            // If the current node is not traceable, we continue with its children
            for(final BuildPipelineNode child : current.getChildren()) {
                collectTraces(run, spanBuffer, buildData, child, parentSpanContext);
            }
            return;
        }

        // If the root span has propagated queue time, we need to adjust all startTime and endTime from Jenkins pipelines spans
        // because this time will be subtracted in the root span. See DatadogTraceBuildLogic#finishBuildTrace method.
        final long propagatedMillisInQueue = Math.max(buildData.getPropagatedMillisInQueue(-1L), 0);
        final long fixedStartTimeNanos = TimeUnit.MICROSECONDS.toNanos(current.getStartTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue));
        final long fixedEndTimeNanos = TimeUnit.MICROSECONDS.toNanos(current.getEndTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue));

        // At this point, the current node is traceable.
        final TraceSpan.TraceSpanContext spanContext = new TraceSpan.TraceSpanContext(parentSpanContext.getTraceId(), parentSpanContext.getSpanId(), current.getSpanId());
        final TraceSpan span = new TraceSpan(buildOperationName(current), fixedStartTimeNanos + getNanosInQueue(current), spanContext);
        span.setServiceName(DatadogUtilities.getDatadogGlobalDescriptor().getCiInstanceName());
        span.setResourceName(current.getName());
        span.setType("ci");
        span.putMeta(CITags.LANGUAGE_TAG_KEY, "");
        span.setError(current.isError());

        final Map<String, Object> traceTags = buildTraceTags(run, current, buildData);
        // Set tags
        for(Map.Entry<String, Object> traceTag : traceTags.entrySet()) {
            if(traceTag.getValue() instanceof Number) {
                span.putMeta(traceTag.getKey(), (Number) traceTag.getValue());
            } else if(traceTag.getValue() instanceof Boolean) {
                span.putMeta(traceTag.getKey(), (Boolean) traceTag.getValue());
            } else {
                span.putMeta(traceTag.getKey(), String.valueOf(traceTag.getValue()));
            }
        }

        //Set metrics
        final Map<String, Long> traceMetrics = buildTraceMetrics(current);
        for(Map.Entry<String, Long> traceMetric : traceMetrics.entrySet()) {
            if(traceMetric.getValue() != null) {
                span.putMetric(traceMetric.getKey(), traceMetric.getValue());
            }
        }

        for(final BuildPipelineNode child : current.getChildren()) {
            collectTraces(run, spanBuffer, buildData, child, span.context());
        }

        //Logs
        //NOTE: Implement sendNodeLogs

        span.setEndNano(fixedEndTimeNanos);

        spanBuffer.add(span);
    }

    private void updateStageBreakdown(final Run<?,?> run, BuildPipelineNode pipelineNode) {
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

        // Pipeline Parameters
        if(!buildData.getBuildParameters().isEmpty()) {
            tags.put(CITags.CI_PARAMETERS, toJson(buildData.getBuildParameters()));
        }

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
        final String rawGitBranch = GitUtils.resolveGitBranch(envVars, buildData);
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

        // If the user set DD_GIT_TAG manually,
        // we override the git.tag value.
        gitTag = GitUtils.resolveGitTag(envVars, buildData);
        if(StringUtils.isNotEmpty(gitTag)){
            tags.put(CITags.GIT_TAG, gitTag);
        }

        // If we could detect a valid commit, the buildData object will contain that commit.
        // If we could not detect a valid commit, that means that the GIT_COMMIT environment variable
        // was overridden by the user at top level, so we set the content what we have (despite it's not valid).
        // We will show a logger.warning at the end of the pipeline.
        final String gitCommit = GitUtils.resolveGitCommit(envVars, buildData);
        if(gitCommit != null && !gitCommit.isEmpty()) {
            tags.put(CITags.GIT_COMMIT__SHA, gitCommit); //Maintain retrocompatibility
            tags.put(CITags.GIT_COMMIT_SHA, gitCommit);
        }

        final String gitRepoUrl = GitUtils.resolveGitRepositoryUrl(envVars, buildData);
        if (gitRepoUrl != null && !gitRepoUrl.isEmpty()) {
            tags.put(CITags.GIT_REPOSITORY_URL, filterSensitiveInfo(gitRepoUrl));
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
        if(!"master".equalsIgnoreCase(nodeName)){
            final String workerHostname = getNodeHostname(run, current);
            // If the worker hostname is equals to controller hostname but the node name is not "master"
            // then we could not detect the worker hostname properly. We set _dd.hostname to 'none' explicitly.
            if(buildData.getHostname("").equalsIgnoreCase(workerHostname)) {
                tags.put(CITags._DD_HOSTNAME, HOSTNAME_NONE);
            } else {
                tags.put(CITags._DD_HOSTNAME, (workerHostname != null) ? workerHostname : HOSTNAME_NONE);
            }
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
            tags.put(CITags.ERROR_MSG, error.getMessage());
            tags.put(CITags.ERROR_TYPE, error.getClass().getName());

            final StringWriter errorString = new StringWriter();
            error.printStackTrace(new PrintWriter(errorString));
            tags.put(CITags.ERROR_STACK, errorString.toString());
        }

        // Propagate Pipeline Name
        tags.put(PIPELINE.getTagName() + CITags._NAME, buildData.getBaseJobName(""));
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

    private String buildOperationName(BuildPipelineNode current) {
        return CI_PROVIDER + "." + current.getType().name().toLowerCase() + ((current.isInternal()) ? ".internal" : "");
    }

}
