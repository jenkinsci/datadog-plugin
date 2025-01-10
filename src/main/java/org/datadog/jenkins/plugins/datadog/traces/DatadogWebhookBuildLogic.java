package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.statusFromResult;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;

import hudson.model.Run;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

/**
 * Keeps the logic to send webhooks related to Jenkins Build.
 * This gets called once per job (datadog level: pipeline)
 */
public class DatadogWebhookBuildLogic extends DatadogBaseBuildLogic {

    private static final Logger logger = Logger.getLogger(DatadogWebhookBuildLogic.class.getName());

    @Nullable
    @Override
    public JSONObject toJson(final BuildData buildData, final Run<?,?> run) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
            return null;
        }

        final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
        if(buildSpanAction == null) {
            return null;
        }

        // Do not submit pipeline trace if start time is not known:
        // the backend relies on start time being constant
        // (otherwise finished pipeline record will not converge with its running pipeline record)
        if (!buildData.isStartTimeKnown()) {
            return null;
        }

        final String jenkinsResult = buildData.getResult("");
        final String status = buildData.isBuilding() ? CITags.STATUS_RUNNING : statusFromResult(jenkinsResult);
        final String prefix = PipelineStepData.StepType.PIPELINE.getTagName();
        final String rawGitBranch = buildData.getBranch("");
        final String gitBranch = normalizeBranch(rawGitBranch);
        // Check if the user set manually the DD_GIT_TAG environment variable.
        // Otherwise, Jenkins reports the tag in the Git branch information. (e.g. origin/tags/0.1.0)
        final String gitTag = Optional.of(buildData.getGitTag(""))
                .filter(tag -> !tag.isEmpty())
                .orElse(normalizeTag(rawGitBranch));

        JSONObject payload = new JSONObject();
        payload.put("payload_version", buildData.getVersion());
        payload.put("level", PipelineStepData.StepType.PIPELINE.getBuildLevel());
        payload.put("url", buildData.getBuildUrl(""));

        final long startTimeMillis = buildData.getStartTime(0L);
        payload.put("start", DatadogUtilities.toISO8601(new Date(startTimeMillis)));

        Long endTime = buildData.getEndTime(null);
        if (!buildData.isBuilding() // do not populate end time for in-progress pipelines
                && endTime != null) {
            payload.put("end", DatadogUtilities.toISO8601(new Date(endTime)));
        }

        payload.put("partial_retry", false);
        payload.put("queue_time", buildData.getTotalQueueTimeMillis());
        payload.put("status", status);
        payload.put("is_manual", isTriggeredManually(run));

        payload.put("trace_id", buildData.getTraceId());
        payload.put("span_id", buildData.getSpanId());

        payload.put("pipeline_id", buildData.getBuildTag(""));
        payload.put("unique_id", buildData.getBuildTag(""));
        payload.put("name", buildData.getJobName());

        // User
        {
            JSONObject userPayload = new JSONObject();
            userPayload.put("name", buildData.getUserId());
            if(StringUtils.isNotEmpty(buildData.getUserEmail(""))){
                userPayload.put("email", buildData.getUserEmail(""));
            }
            payload.put("user", userPayload);
        }

        // Pipeline Parameters
        if (!buildData.getBuildParameters().isEmpty()) {
            JSONObject parametersPayload = new JSONObject();
            for (Map.Entry<String, String> parameter : buildData.getBuildParameters().entrySet()) {
                parametersPayload.put(parameter.getKey(), parameter.getValue());
            }
            payload.put("parameters", parametersPayload);
        }

        // Tags
        // Here we include both global tags and fields that are not supported as regular fields by the webhooks intake
        {
            JSONArray tagsPayload = new JSONArray();

            Map<String, String> globalTags = new HashMap<>(buildData.getTagsForTraces());
            globalTags.putAll(TagsUtil.convertTagsToMapSingleValues(DatadogUtilities.getTagsFromPipelineAction(run)));

            for(Map.Entry<String, String> tagEntry : globalTags.entrySet()) {
                tagsPayload.add(tagEntry.getKey() + ":" + tagEntry.getValue());
            }

            // Jenkins specific
            tagsPayload.add(CITags._DD_CI_INTERNAL + ":" + "false");
            tagsPayload.add(CITags.JENKINS_PLUGIN_VERSION + ":" + DatadogUtilities.getDatadogPluginVersion());
            tagsPayload.add(CITags.JENKINS_TAG + ":" + buildData.getBuildTag(""));

            String executorNumber = buildData.getExecutorNumber("");
            if (StringUtils.isNotEmpty(executorNumber)) {
                tagsPayload.add(CITags.JENKINS_EXECUTOR_NUMBER  + ":" + executorNumber);
            }

            if (StringUtils.isNotEmpty(jenkinsResult)) {
                tagsPayload.add(CITags.JENKINS_RESULT + ":" + jenkinsResult.toLowerCase());
            }
            tagsPayload.add(prefix + CITags._NUMBER + ":" + buildData.getBuildNumber(""));

            // For backwards compat
            tagsPayload.add(prefix + CITags._RESULT + ":" + status);

            // Configurations
            final Map<String, String> configurations = buildData.getBuildConfigurations();
            if(!configurations.isEmpty()){
                for(Map.Entry<String, String> entry : configurations.entrySet()) {
                    tagsPayload.add(prefix + CITags._CONFIGURATION + "." + entry.getKey() + ":" + entry.getValue());
                }
            }

            // Stage breakdown
            final String stagesJson = getStageBreakdown(run);
            if (stagesJson != null) {
                tagsPayload.add(CITags._DD_CI_STAGES + ":" + stagesJson);
            }

            payload.put("tags", tagsPayload);
        }

        // Node
        {
            JSONObject nodePayload = new JSONObject();

            // It seems like "built-in" node as the default value does not have much practical sense.
            // It is done to preserve existing behavior (note that this logic is not applied to metrics - also to preserve the plugin's existing behavior).
            // The mechanism before the changes was the following:
            // - DatadogBuildListener#onInitialize created a BuildData instance
            // - that BuildData had its nodeName populated from environment variables obtained from Run
            // - the instance was persisted in an Action attached to Run, and was used to populate the node name of the pipeline span (always as the last fallback)
            // For pipelines, the environment variables that Run#getEnvironment returns at the beginning of the run always (!) contain NODE_NAME = "built-in" (when invoked at the end of the run, the env will have a different set of variables).
            // This is true regardless of whether the pipeline definition has a top-level agent block or not.
            // For freestyle projects the correct NODE_NAME seems to be available in the run's environment variables at every stage of the build's lifecycle.
            final String nodeName = buildData.getNodeName("built-in");
            nodePayload.put("name", nodeName);
            if(!DatadogUtilities.isMainNode(nodeName)) {
                final String workerHostname = buildData.getHostname("");
                nodePayload.put("hostname", !workerHostname.isEmpty() ? workerHostname : HOSTNAME_NONE);
            } else {
                nodePayload.put("hostname", DatadogUtilities.getHostname(null));
            }

            final String workspace = buildData.getWorkspace("");
            nodePayload.put("workspace", workspace);

            final Set<String> nodeLabels = getNodeLabels(run, nodeName);
            nodePayload.put("labels", JSONArray.fromObject((nodeLabels)));

            payload.put("node", nodePayload);
        }

        // Git info
        {
            JSONObject gitPayload = new JSONObject();

            if(StringUtils.isNotEmpty(gitBranch)) {
                gitPayload.put("branch", gitBranch);
            }

            if(StringUtils.isNotEmpty(gitTag)) {
                gitPayload.put("tag", gitTag);
            }

            final String gitCommit = buildData.getGitCommit("");
            if(!isValidCommit(gitCommit)) {
                logger.warning("Couldn't find a valid commit for pipelineID '"+buildData.getBuildTag("")+"'. GIT_COMMIT environment variable was not found or has invalid SHA1 string: " + gitCommit);
            } else {
                gitPayload.put("sha", gitCommit);
            }

            final String gitRepoUrl = buildData.getGitUrl("");
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

            payload.put("git", gitPayload);
        }

        // Upstream pipeline info
        Long upstreamPipelineTraceId = buildData.getUpstreamPipelineTraceId();
        String upstreamPipelineUrl = buildData.getUpstreamPipelineUrl();
        if (upstreamPipelineTraceId != null && upstreamPipelineUrl != null) {
            JSONObject upstreamPayload = new JSONObject();
            upstreamPayload.put("trace_id", upstreamPipelineTraceId);
            upstreamPayload.put("url", upstreamPipelineUrl);
            payload.put("parent_pipeline", upstreamPayload);
        }

        return payload;
    }

}
