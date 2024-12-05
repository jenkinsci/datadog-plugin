package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.model.PipelineStepData.StepType.PIPELINE;
import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;

import hudson.model.Run;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;
import org.datadog.jenkins.plugins.datadog.model.Status;
import org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;


/**
 * Keeps the logic to send traces related to inner jobs of Jenkins Pipelines (datadog levels: stage and job).
 * The top-level job (datadog level: pipeline) is handled by DatadogTraceBuildLogic
 */
public class DatadogTracePipelineLogic extends DatadogBasePipelineLogic {

    private final JsonTraceSpanMapper jsonTraceSpanMapper = new JsonTraceSpanMapper();

    @Nonnull
    @Override
    public JSONObject toJson(PipelineStepData flowNode, Run<?, ?> run) throws IOException, InterruptedException {
        TraceSpan span = toSpan(flowNode, run);
        return jsonTraceSpanMapper.map(span);
    }

    // hook for tests
    @Nonnull
    public TraceSpan toSpan(PipelineStepData current, Run<?, ?> run) throws IOException, InterruptedException {
        BuildData buildData = new BuildData(run, DatadogUtilities.getTaskListener(run));

        final long startTimeNanos = TimeUnit.MILLISECONDS.toNanos(current.getStartTimeMillis());
        final long endTimeNanos = TimeUnit.MILLISECONDS.toNanos(current.getEndTimeMillis());

        // At this point, the current node is traceable.
        final TraceSpan.TraceSpanContext spanContext = new TraceSpan.TraceSpanContext(current.getTraceId(), current.getParentSpanId(), current.getSpanId());
        final TraceSpan span = new TraceSpan(buildOperationName(current), startTimeNanos, spanContext);
        span.setEndNano(endTimeNanos);
        span.setServiceName(DatadogUtilities.getDatadogGlobalDescriptor().getCiInstanceName());
        span.setResourceName(current.getName());
        span.setType("ci");
        span.putMeta(CITags.LANGUAGE_TAG_KEY, "");
        span.setError(current.isError() || current.isUnstable());

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

        //Logs
        //NOTE: Implement sendNodeLogs

        return span;
    }

    private Map<String, Long> buildTraceMetrics(PipelineStepData current) {
        final Map<String, Long> metrics = new HashMap<>();
        metrics.put(CITags.QUEUE_TIME, TimeUnit.MILLISECONDS.toSeconds(current.getQueueTimeMillis()));
        return metrics;
    }

    private Map<String, Object> buildTraceTags(final Run<?, ?> run, final PipelineStepData current, final BuildData buildData) {
        final String prefix = current.getType().getTagName();
        final String buildLevel = current.getType().getBuildLevel();

        final Map<String, Object> tags = new HashMap<>();
        tags.put(CITags.CI_PROVIDER_NAME, CI_PROVIDER);
        tags.put(CITags._DD_ORIGIN, ORIGIN_CIAPP_PIPELINE);
        tags.put(prefix + CITags._NAME, current.getName());
        tags.put(prefix + CITags._NUMBER, current.getId());
        Status status = current.getStatus();
        tags.put(prefix + CITags._RESULT, status.toTag());
        tags.put(CITags.STATUS, status.toTag());

        // Pipeline Parameters
        if(!buildData.getBuildParameters().isEmpty()) {
            tags.put(CITags.CI_PARAMETERS, DatadogUtilities.toJson(buildData.getBuildParameters()));
        }

        String url = buildData.getBuildUrl("");
        if(StringUtils.isNotBlank(url)) {
            tags.put(prefix + CITags._URL, url + "execution/node/"+current.getId()+"/");
        }

        final String workspace = firstNonNull(current.getWorkspace(), buildData.getWorkspace(""));
        tags.put(CITags.WORKSPACE_PATH, workspace);

        tags.put(CITags._DD_CI_INTERNAL, false);
        tags.put(CITags._DD_CI_BUILD_LEVEL, buildLevel);
        tags.put(CITags._DD_CI_LEVEL, buildLevel);

        String jenkinsResult = current.getJenkinsResult();
        if (jenkinsResult != null) {
            tags.put(CITags.JENKINS_RESULT, jenkinsResult.toLowerCase());
        }

        String executorNumber = current.getExecutorNumber();
        if (StringUtils.isNotEmpty(executorNumber))  {
            tags.put(CITags.JENKINS_EXECUTOR_NUMBER, executorNumber);
        }

        tags.put(CITags.ERROR, String.valueOf(current.isError() || current.isUnstable()));

        //Git Info
        String rawGitBranch = buildData.getBranch("");
        String gitBranch;
        String gitTag;
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
        gitTag = buildData.getGitTag("");
        if(StringUtils.isNotEmpty(gitTag)){
            tags.put(CITags.GIT_TAG, gitTag);
        }

        // If we could detect a valid commit, the buildData object will contain that commit.
        // If we could not detect a valid commit, that means that the GIT_COMMIT environment variable
        // was overridden by the user at top level, so we set the content what we have (despite it's not valid).
        // We will show a logger.warning at the end of the pipeline.
        String gitCommit = buildData.getGitCommit("");
        if(gitCommit != null && !gitCommit.isEmpty()) {
            tags.put(CITags.GIT_COMMIT__SHA, gitCommit); //Maintain retrocompatibility
            tags.put(CITags.GIT_COMMIT_SHA, gitCommit);
        }

        String gitRepoUrl = buildData.getGitUrl("");
        if (gitRepoUrl != null && !gitRepoUrl.isEmpty()) {
            tags.put(CITags.GIT_REPOSITORY_URL, filterSensitiveInfo(gitRepoUrl));
        }

        // User info
        String user = buildData.getUserId();
        tags.put(CITags.USER_NAME, user);

        // Node info
        final String nodeName = getNodeName(current, buildData);
        tags.put(CITags.NODE_NAME, nodeName);

        final String nodeLabels = DatadogUtilities.toJson(getNodeLabels(run, current, nodeName));
        if(nodeLabels != null && !nodeLabels.isEmpty()){
            tags.put(CITags.NODE_LABELS, nodeLabels);
        } else {
            tags.put(CITags.NODE_LABELS, "[]");
        }

        // If the NodeName == "master", we don't set _dd.hostname. It will be overridden by the Datadog Agent. (Traces are only available using Datadog Agent)
        if(!DatadogUtilities.isMainNode(nodeName)) {
            final String workerHostname = getNodeHostname(current, buildData);
            tags.put(CITags._DD_HOSTNAME, !workerHostname.isEmpty() ? workerHostname : HOSTNAME_NONE);
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
        } else if(current.isUnstable() && current.getUnstableMessage() != null){
            tags.put(CITags.ERROR_MSG, current.getUnstableMessage());
            tags.put(CITags.ERROR_TYPE, "unstable");
        }

        // Propagate Pipeline Name
        tags.put(PIPELINE.getTagName() + CITags._NAME, buildData.getJobName());
        tags.put(PIPELINE.getTagName() + CITags._ID, buildData.getBuildTag(""));

        // Propagate Stage Name
        if(!PipelineStepData.StepType.STAGE.equals(current.getType()) && current.getStageName() != null) {
            tags.put(PipelineStepData.StepType.STAGE.getTagName() + CITags._NAME, current.getStageName());
        }

        Map<String, String> globalTags = new HashMap<>(buildData.getTagsForTraces());
        globalTags.putAll(TagsUtil.convertTagsToMapSingleValues(current.getTags()));
        tags.putAll(globalTags);

        return tags;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

}
