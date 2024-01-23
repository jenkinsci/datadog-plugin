package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.statusFromResult;
import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;

import hudson.model.Result;
import hudson.model.Run;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

/**
 * Keeps the logic to create traces related to Jenkins Build.
 * This gets called once per job (datadog level: pipeline)
 */
public class DatadogTraceBuildLogic extends DatadogBaseBuildLogic {

    private static final Logger logger = Logger.getLogger(DatadogTraceBuildLogic.class.getName());

    private final JsonTraceSpanMapper jsonTraceSpanMapper = new JsonTraceSpanMapper();

    @Nullable
    @Override
    public JSONObject toJson(final BuildData buildData, final Run<?,?> run) {
        TraceSpan span = toSpan(buildData, run);
        return span != null ? jsonTraceSpanMapper.map(span) : null;
    }

    @Nullable
    // hook for tests
    public TraceSpan toSpan(final BuildData buildData, final Run<?,?> run) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
            return null;
        }

        final TraceSpan buildSpan = BuildSpanManager.get().get(buildData.getBuildTag(""));
        if(buildSpan == null) {
            return null;
        }

        final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
        if(buildSpanAction == null) {
            return null;
        }

        final String prefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();
        final String buildLevel = BuildPipelineNode.NodeType.PIPELINE.getBuildLevel();
        final long endTimeMicros = buildData.getEndTime(0L) * 1000;

        buildSpan.setServiceName(DatadogUtilities.getDatadogGlobalDescriptor().getCiInstanceName());
        buildSpan.setType("ci");
        buildSpan.putMeta(CITags.CI_PROVIDER_NAME, "jenkins");
        buildSpan.putMeta(CITags._DD_CI_INTERNAL, "false");
        buildSpan.putMeta(CITags._DD_CI_BUILD_LEVEL, buildLevel);
        buildSpan.putMeta(CITags._DD_CI_LEVEL, buildLevel);
        buildSpan.putMeta(CITags.IS_MANUAL, isTriggeredManually(run));
        buildSpan.putMeta(CITags._DD_ORIGIN, ORIGIN_CIAPP_PIPELINE);
        buildSpan.putMeta(CITags.USER_NAME, buildData.getUserId());
        if(StringUtils.isNotEmpty(buildData.getUserEmail(""))){
            buildSpan.putMeta(CITags.USER_EMAIL, buildData.getUserEmail(""));
        }
        buildSpan.putMeta(prefix + CITags._ID, buildData.getBuildTag(""));
        buildSpan.putMeta(prefix + CITags._NUMBER, buildData.getBuildNumber(""));
        buildSpan.putMeta(prefix + CITags._URL, buildData.getBuildUrl(""));
        buildSpan.putMetric(CITags.QUEUE_TIME, TimeUnit.MILLISECONDS.toSeconds(getMillisInQueue(buildData)));

        // Pipeline Parameters
        if(!buildData.getBuildParameters().isEmpty()) {
            buildSpan.putMeta(CITags.CI_PARAMETERS, DatadogUtilities.toJson(buildData.getBuildParameters()));
        }

        buildSpan.putMeta(CITags.WORKSPACE_PATH, buildData.getWorkspace(""));
        buildSpan.putMeta(CITags.NODE_NAME, buildData.getNodeName(""));

        final String nodeLabelsJson = DatadogUtilities.toJson(getNodeLabels(run, buildData.getNodeName("")));
        if(nodeLabelsJson != null && !nodeLabelsJson.isEmpty()){
            buildSpan.putMeta(CITags.NODE_LABELS, nodeLabelsJson);
        } else {
            buildSpan.putMeta(CITags.NODE_LABELS, "[]");
        }

        // If the NodeName == "master", we don't set _dd.hostname. It will be overridden by the Datadog Agent. (Traces are only available using Datadog Agent)
        if(!DatadogUtilities.isMainNode(buildData.getNodeName(""))) {
            final String workerHostname = buildData.getHostname("");
            buildSpan.putMeta(CITags._DD_HOSTNAME, !workerHostname.isEmpty() ? workerHostname : HOSTNAME_NONE);
        }

        // Git Info
        final String gitUrl = buildData.getGitUrl("");
        if(StringUtils.isNotEmpty(gitUrl)){
            buildSpan.putMeta(CITags.GIT_REPOSITORY_URL, filterSensitiveInfo(gitUrl));
        }

        final String gitCommit = buildData.getGitCommit("");
        if(!isValidCommit(gitCommit)) {
            logger.warning("Couldn't find a valid commit for pipelineID '"+buildData.getBuildTag("")+"'. GIT_COMMIT environment variable was not found or has invalid SHA1 string: " + gitCommit);
        }

        if(StringUtils.isNotEmpty(gitCommit)){
            buildSpan.putMeta(CITags.GIT_COMMIT__SHA, gitCommit); //Maintain retrocompatibility
            buildSpan.putMeta(CITags.GIT_COMMIT_SHA, gitCommit);
        }

        final String gitMessage = buildData.getGitMessage("");
        if(StringUtils.isNotEmpty(gitMessage)){
            buildSpan.putMeta(CITags.GIT_COMMIT_MESSAGE, gitMessage);
        }

        final String gitAuthor = buildData.getGitAuthorName("");
        if(StringUtils.isNotEmpty(gitAuthor)){
            buildSpan.putMeta(CITags.GIT_COMMIT_AUTHOR_NAME, gitAuthor);
        }

        final String gitAuthorEmail = buildData.getGitAuthorEmail("");
        if(StringUtils.isNotEmpty(gitAuthorEmail)){
            buildSpan.putMeta(CITags.GIT_COMMIT_AUTHOR_EMAIL, gitAuthorEmail);
        }

        final String gitAuthorDate = buildData.getGitAuthorDate("");
        if(StringUtils.isNotEmpty(gitAuthorDate)){
            buildSpan.putMeta(CITags.GIT_COMMIT_AUTHOR_DATE, gitAuthorDate);
        }

        final String gitCommitter = buildData.getGitCommitterName("");
        if(StringUtils.isNotEmpty(gitCommitter)){
            buildSpan.putMeta(CITags.GIT_COMMIT_COMMITTER_NAME, gitCommitter);
        }

        final String gitCommitterEmail = buildData.getGitCommitterEmail("");
        if(StringUtils.isNotEmpty(gitCommitterEmail)){
            buildSpan.putMeta(CITags.GIT_COMMIT_COMMITTER_EMAIL, gitCommitterEmail);
        }

        final String gitCommitterDate = buildData.getGitCommitterDate("");
        if(StringUtils.isNotEmpty(gitCommitterDate)){
            buildSpan.putMeta(CITags.GIT_COMMIT_COMMITTER_DATE, gitCommitterDate);
        }

        final String gitDefaultBranch = buildData.getGitDefaultBranch("");
        if(StringUtils.isNotEmpty(gitDefaultBranch)){
            buildSpan.putMeta(CITags.GIT_DEFAULT_BRANCH, gitDefaultBranch);
        }

        final String rawGitBranch = buildData.getBranch("");
        final String gitBranch = normalizeBranch(rawGitBranch);
        if(StringUtils.isNotEmpty(gitBranch)) {
            buildSpan.putMeta(CITags.GIT_BRANCH, gitBranch);
        }

        // Check if the user set manually the DD_GIT_TAG environment variable.
        // Otherwise, Jenkins reports the tag in the Git branch information. (e.g. origin/tags/0.1.0)
        final String gitTag = Optional.of(buildData.getGitTag(""))
                .filter(tag -> !tag.isEmpty())
                .orElse(normalizeTag(rawGitBranch));
        if(StringUtils.isNotEmpty(gitTag)) {
            buildSpan.putMeta(CITags.GIT_TAG, gitTag);
        }

        buildSpan.setResourceName(buildData.getBaseJobName(""));
        buildSpan.putMeta(prefix + CITags._NAME, buildData.getBaseJobName(""));

        final String fullJobName = buildData.getJobName("");
        final String gitRef = gitBranch != null ? gitBranch : gitTag;
        final Map<String, String> configurations = JobNameConfigurationParser.getConfigurations(fullJobName, gitRef);
        if(!configurations.isEmpty()){
            for(Map.Entry<String, String> entry : configurations.entrySet()) {
                buildSpan.putMeta(prefix + CITags._CONFIGURATION + "." + entry.getKey(), entry.getValue());
            }
        }

        // Stage breakdown
        final String stagesJson = getStageBreakdown(run);
        if (stagesJson != null) {
            buildSpan.putMeta(CITags._DD_CI_STAGES, stagesJson);
        }

        // Jenkins specific
        buildSpan.putMeta(CITags.JENKINS_TAG, buildData.getBuildTag(""));
        buildSpan.putMeta(CITags.JENKINS_EXECUTOR_NUMBER, buildData.getExecutorNumber(""));

        final String jenkinsResult = buildData.getResult("");
        final String pipelineResult = statusFromResult(jenkinsResult);
        buildSpan.putMeta(prefix + CITags._RESULT, pipelineResult);
        buildSpan.putMeta(CITags.STATUS, pipelineResult);
        buildSpan.putMeta(CITags.JENKINS_RESULT, jenkinsResult.toLowerCase());

        if(Result.FAILURE.toString().equals(jenkinsResult) || Result.UNSTABLE.toString().equals(jenkinsResult)) {
            buildSpan.setError(true);
        }

        Map<String, String> globalTags = new HashMap<>(buildData.getTagsForTraces());
        globalTags.putAll(TagsUtil.convertTagsToMapSingleValues(DatadogUtilities.getTagsFromPipelineAction(run)));

        for(Map.Entry<String, String> tagEntry : globalTags.entrySet()) {
            buildSpan.putMeta(tagEntry.getKey(), tagEntry.getValue());
        }

        // If the build is a Jenkins Pipeline, the queue time is included in the root span duration.
        // We need to adjust the endTime of the root span subtracting the queue time reported by its child span.
        // The propagated queue time is set DatadogTracePipelineLogic#updateBuildData method.
        // The queue time reported by DatadogBuildListener#onStarted method is not included in the root span duration.
        final long propagatedMillisInQueue = Math.max(buildData.getPropagatedMillisInQueue(-1L), 0);

        // Although the queue time happens before the span startTime, we cannot remove it from the startTime
        // because there is no API to do it at the end of the trace. Additionally, we cannot create the root span
        // at the end of the build, because we would lose the logs correlation.
        // When the root span starts, we don't have the propagated queue time yet. We need to wait till the
        // end of the pipeline execution and do it in the endTime, adjusting all child spans if needed.
        buildSpan.setEndNano(TimeUnit.MICROSECONDS.toNanos(endTimeMicros - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue)));

        return buildSpan;
    }

}
