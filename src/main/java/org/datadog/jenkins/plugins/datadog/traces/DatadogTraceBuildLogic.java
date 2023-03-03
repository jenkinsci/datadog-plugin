package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.statusFromResult;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.toJson;
import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.CIGlobalTagsAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineQueueInfoAction;
import org.datadog.jenkins.plugins.datadog.model.StageBreakdownAction;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.transport.HttpClient;

import hudson.model.Result;
import hudson.model.Run;

/**
 * Keeps the logic to send traces related to Jenkins Build.
 */
public class DatadogTraceBuildLogic extends DatadogBaseBuildLogic {

    private static final Logger logger = Logger.getLogger(DatadogTraceBuildLogic.class.getName());

    private final HttpClient agentHttpClient;

    public DatadogTraceBuildLogic(final HttpClient agentHttpClient) {
        this.agentHttpClient = agentHttpClient;
    }

    @Override
    public void startBuildTrace(final BuildData buildData, Run run) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
            logger.fine("CI Visibility is disabled");
            return;
        }

        // Traces
        if(this.agentHttpClient == null) {
            logger.severe("Unable to send build traces. Tracer is null");
            return;
        }

        final TraceSpan buildSpan = new TraceSpan("jenkins.build", TimeUnit.MILLISECONDS.toNanos(buildData.getStartTime(0L)));
        BuildSpanManager.get().put(buildData.getBuildTag(""), buildSpan);

        // The buildData object is stored in the BuildSpanAction to be updated
        // by the information that will be calculated when the pipeline listeners
        // were executed. This is needed because if the user build is based on
        // Jenkins Pipelines, there are many information that is missing when the
        // root span is created, such as Git info (this is calculated in an inner step
        // of the pipeline)
        final BuildSpanAction buildSpanAction = new BuildSpanAction(buildData, buildSpan.context());
        run.addAction(buildSpanAction);

        final StepDataAction stepDataAction = new StepDataAction();
        run.addAction(stepDataAction);

        final StepTraceDataAction stepTraceDataAction = new StepTraceDataAction();
        run.addAction(stepTraceDataAction);

        final StageBreakdownAction stageBreakdownAction = new StageBreakdownAction();
        run.addAction(stageBreakdownAction);

        final PipelineQueueInfoAction pipelineQueueInfoAction = new PipelineQueueInfoAction();
        run.addAction(pipelineQueueInfoAction);

        final CIGlobalTagsAction ciGlobalTags = new CIGlobalTagsAction(buildData.getTagsForTraces());
        run.addAction(ciGlobalTags);
    }

    @Override
    public void finishBuildTrace(final BuildData buildData, final Run<?,?> run) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
            return;
        }

        final TraceSpan buildSpan = BuildSpanManager.get().remove(buildData.getBuildTag(""));
        if(buildSpan == null) {
            return;
        }

        final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
        if(buildSpanAction == null) {
            return;
        }

        // In this point of the execution, the BuildData stored within
        // BuildSpanAction has been updated by the information available
        // inside the Pipeline steps. (Only applicable if the build is
        // based on Jenkins Pipelines).
        final BuildData updatedBuildData = buildSpanAction.getBuildData();

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
        buildSpan.putMetric(CITags.QUEUE_TIME, TimeUnit.MILLISECONDS.toSeconds(getMillisInQueue(updatedBuildData)));

        // Pipeline Parameters
        if(!buildData.getBuildParameters().isEmpty()) {
            buildSpan.putMeta(CITags.CI_PARAMETERS, toJson(buildData.getBuildParameters()));
        }

        final String workspace = buildData.getWorkspace("").isEmpty() ? updatedBuildData.getWorkspace("") : buildData.getWorkspace("");
        buildSpan.putMeta(CITags.WORKSPACE_PATH, workspace);

        final String nodeName = getNodeName(run, buildData, updatedBuildData);
        buildSpan.putMeta(CITags.NODE_NAME, nodeName);

        final String nodeLabelsJson = toJson(getNodeLabels(run, nodeName));
        if(!nodeLabelsJson.isEmpty()){
            buildSpan.putMeta(CITags.NODE_LABELS, nodeLabelsJson);
        }

        // If the NodeName == "master", we don't set _dd.hostname. It will be overridden by the Datadog Agent. (Traces are only available using Datadog Agent)
        if(!DatadogUtilities.isMainNode(nodeName)) {
            final String workerHostname = getNodeHostname(run, updatedBuildData);
            // If the worker hostname is equals to controller hostname but the node name is not master/built-in then we
            // could not detect the worker hostname properly. Check if it's set in the environment, otherwise set to none.
            if(buildData.getHostname("").equalsIgnoreCase(workerHostname)) {
                String envHostnameOrNone = DatadogUtilities.getHostnameFromWorkerEnv(run).orElse(HOSTNAME_NONE);
                buildSpan.putMeta(CITags._DD_HOSTNAME, envHostnameOrNone);
            } else {
                buildSpan.putMeta(CITags._DD_HOSTNAME, (workerHostname != null) ? workerHostname : HOSTNAME_NONE);
            }
        }

        // Git Info
        final String gitUrl = buildData.getGitUrl("").isEmpty() ? updatedBuildData.getGitUrl("") : buildData.getGitUrl("");
        if(StringUtils.isNotEmpty(gitUrl)){
            buildSpan.putMeta(CITags.GIT_REPOSITORY_URL, filterSensitiveInfo(gitUrl));
        }

        final String gitCommit = buildData.getGitCommit("").isEmpty() ? updatedBuildData.getGitCommit("") : buildData.getGitCommit("");
        if(!isValidCommit(gitCommit)) {
            logger.warning("Couldn't find a valid commit for pipelineID '"+buildData.getBuildTag("")+"'. GIT_COMMIT environment variable was not found or has invalid SHA1 string: " + gitCommit);
        }

        if(StringUtils.isNotEmpty(gitCommit)){
            buildSpan.putMeta(CITags.GIT_COMMIT__SHA, gitCommit); //Maintain retrocompatibility
            buildSpan.putMeta(CITags.GIT_COMMIT_SHA, gitCommit);
        }

        final String gitMessage = buildData.getGitMessage("").isEmpty() ? updatedBuildData.getGitMessage("") : buildData.getGitMessage("");
        if(StringUtils.isNotEmpty(gitMessage)){
            buildSpan.putMeta(CITags.GIT_COMMIT_MESSAGE, gitMessage);
        }

        final String gitAuthor = buildData.getGitAuthorName("").isEmpty() ? updatedBuildData.getGitAuthorName("") : buildData.getGitAuthorName("");
        if(StringUtils.isNotEmpty(gitAuthor)){
            buildSpan.putMeta(CITags.GIT_COMMIT_AUTHOR_NAME, gitAuthor);
        }

        final String gitAuthorEmail = buildData.getGitAuthorEmail("").isEmpty() ? updatedBuildData.getGitAuthorEmail("") : buildData.getGitAuthorEmail("");
        if(StringUtils.isNotEmpty(gitAuthorEmail)){
            buildSpan.putMeta(CITags.GIT_COMMIT_AUTHOR_EMAIL, gitAuthorEmail);
        }

        final String gitAuthorDate = buildData.getGitAuthorDate("").isEmpty() ? updatedBuildData.getGitAuthorDate("") : buildData.getGitAuthorDate("");
        if(StringUtils.isNotEmpty(gitAuthorDate)){
            buildSpan.putMeta(CITags.GIT_COMMIT_AUTHOR_DATE, gitAuthorDate);
        }

        final String gitCommitter = buildData.getGitCommitterName("").isEmpty() ? updatedBuildData.getGitCommitterName("") : buildData.getGitCommitterName("");
        if(StringUtils.isNotEmpty(gitCommitter)){
            buildSpan.putMeta(CITags.GIT_COMMIT_COMMITTER_NAME, gitCommitter);
        }

        final String gitCommitterEmail = buildData.getGitCommitterEmail("").isEmpty() ? updatedBuildData.getGitCommitterEmail("") : buildData.getGitCommitterEmail("");
        if(StringUtils.isNotEmpty(gitCommitterEmail)){
            buildSpan.putMeta(CITags.GIT_COMMIT_COMMITTER_EMAIL, gitCommitterEmail);
        }

        final String gitCommitterDate = buildData.getGitCommitterDate("").isEmpty() ? updatedBuildData.getGitCommitterDate("") : buildData.getGitCommitterDate("");
        if(StringUtils.isNotEmpty(gitCommitterDate)){
            buildSpan.putMeta(CITags.GIT_COMMIT_COMMITTER_DATE, gitCommitterDate);
        }

        final String gitDefaultBranch = buildData.getGitDefaultBranch("").isEmpty() ? updatedBuildData.getGitDefaultBranch("") : buildData.getGitDefaultBranch("");
        if(StringUtils.isNotEmpty(gitDefaultBranch)){
            buildSpan.putMeta(CITags.GIT_DEFAULT_BRANCH, gitDefaultBranch);
        }

        final String rawGitBranch = buildData.getBranch("").isEmpty() ? updatedBuildData.getBranch("") : buildData.getBranch("");
        final String gitBranch = normalizeBranch(rawGitBranch);
        if(StringUtils.isNotEmpty(gitBranch)) {
            buildSpan.putMeta(CITags.GIT_BRANCH, gitBranch);
        }

        // Check if the user set manually the DD_GIT_TAG environment variable.
        // Otherwise, Jenkins reports the tag in the Git branch information. (e.g. origin/tags/0.1.0)
        final String gitTag = Optional.of(buildData.getGitTag("").isEmpty() ? updatedBuildData.getGitTag("") : buildData.getGitTag(""))
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

        // CI Tags propagation
        final CIGlobalTagsAction ciGlobalTagsAction = run.getAction(CIGlobalTagsAction.class);
        if(ciGlobalTagsAction != null) {
            final Map<String, String> tags = ciGlobalTagsAction.getTags();
            for(Map.Entry<String, String> tagEntry : tags.entrySet()) {
                buildSpan.putMeta(tagEntry.getKey(), tagEntry.getValue());
            }
        }

        // If the build is a Jenkins Pipeline, the queue time is included in the root span duration.
        // We need to adjust the endTime of the root span subtracting the queue time reported by its child span.
        // The propagated queue time is set DatadogTracePipelineLogic#updateBuildData method.
        // The queue time reported by DatadogBuildListener#onStarted method is not included in the root span duration.
        final long propagatedMillisInQueue = Math.max(updatedBuildData.getPropagatedMillisInQueue(-1L), 0);

        // Although the queue time happens before the span startTime, we cannot remove it from the startTime
        // because there is no API to do it at the end of the trace. Additionally, we cannot create the root span
        // at the end of the build, because we would lose the logs correlation.
        // When the root span starts, we don't have the propagated queue time yet. We need to wait till the
        // end of the pipeline execution and do it in the endTime, adjusting all child spans if needed.
        buildSpan.setEndNano(TimeUnit.MICROSECONDS.toNanos(endTimeMicros - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue)));
        agentHttpClient.send(Collections.singletonList(buildSpan));
    }

}
