package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.statusFromResult;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;

import hudson.model.Run;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.CIGlobalTagsAction;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;

/**
 * Keeps the logic to send webhooks related to Jenkins Build.
 * This gets called once per job (datadog level: pipeline)
 */
public class DatadogWebhookBuildLogic extends DatadogBaseBuildLogic {

    private static final Logger logger = Logger.getLogger(DatadogWebhookBuildLogic.class.getName());

    @Override
    public JSONObject finishBuildTrace(final BuildData buildData, final Run<?,?> run) {
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

        // In this point of the execution, the BuildData stored within
        // BuildSpanAction has been updated by the information available
        // inside the Pipeline steps by DatadogWebhookPipelineLogic.
        // (Only applicable if the build is based on Jenkins Pipelines).
        final BuildData updatedBuildData = buildSpanAction.getBuildData();

        final long startTimeMillis = buildData.getStartTime(0L);
        // If the build is a Jenkins Pipeline, the queue time is included in the root duration.
        // We need to adjust the endTime of the root subtracting the queue time reported by its children.
        // The propagated queue time is set DatadogTracePipelineLogic#updateBuildData method.
        // The queue time reported by DatadogBuildListener#onStarted method is not included in the root duration.
        final long propagatedMillisInQueue = Math.max(updatedBuildData.getPropagatedMillisInQueue(-1L), 0);
        // Although the queue time happens before the startTime, we cannot remove it from the startTime
        // because there is no API to do it at the end of the trace. Additionally, we cannot create the root
        // at the end of the build, because we would lose the logs correlation.
        // When the root starts, we don't have the propagated queue time yet. We need to wait till the
        // end of the pipeline execution and do it in the endTime, adjusting all children if needed.
        final long endTimeMillis = buildData.getEndTime(0L) - propagatedMillisInQueue;
        final String jenkinsResult = buildData.getResult("");
        final String status = statusFromResult(jenkinsResult);
        final String prefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();
        final String rawGitBranch = buildData.getBranch("").isEmpty() ? updatedBuildData.getBranch("") : buildData.getBranch("");
        final String gitBranch = normalizeBranch(rawGitBranch);
        // Check if the user set manually the DD_GIT_TAG environment variable.
        // Otherwise, Jenkins reports the tag in the Git branch information. (e.g. origin/tags/0.1.0)
        final String gitTag = Optional.of(buildData.getGitTag("").isEmpty() ? updatedBuildData.getGitTag("") : buildData.getGitTag(""))
                .filter(tag -> !tag.isEmpty())
                .orElse(normalizeTag(rawGitBranch));

        JSONObject payload = new JSONObject();
        payload.put("level", BuildPipelineNode.NodeType.PIPELINE.getBuildLevel());
        payload.put("url", buildData.getBuildUrl(""));
        payload.put("start", DatadogUtilities.toISO8601(new Date(startTimeMillis)));
        payload.put("end", DatadogUtilities.toISO8601(new Date(endTimeMillis)));
        payload.put("partial_retry", false);
        payload.put("queue_time", getMillisInQueue(updatedBuildData));
        payload.put("status", status);
        payload.put("is_manual", isTriggeredManually(run));

        payload.put("trace_id", buildSpan.context().getTraceId());
        payload.put("span_id", buildSpan.context().getSpanId());

        payload.put("pipeline_id", buildData.getBuildTag(""));
        payload.put("unique_id", buildData.getBuildTag(""));
        payload.put("name", buildData.getBaseJobName(""));

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

            final CIGlobalTagsAction ciGlobalTagsAction = run.getAction(CIGlobalTagsAction.class);
            if(ciGlobalTagsAction != null) {
                final Map<String, String> tags = ciGlobalTagsAction.getTags();
                for(Map.Entry<String, String> tagEntry : tags.entrySet()) {
                    tagsPayload.add(tagEntry.getKey() + ":" + tagEntry.getValue());
                }
            }

            // Jenkins specific
            tagsPayload.add(CITags._DD_CI_INTERNAL + ":" + "false");
            tagsPayload.add(CITags.JENKINS_TAG + ":" + buildData.getBuildTag(""));
            tagsPayload.add(CITags.JENKINS_EXECUTOR_NUMBER  + ":" + buildData.getExecutorNumber(""));
            if (StringUtils.isNotEmpty(jenkinsResult)) {
                tagsPayload.add(CITags.JENKINS_RESULT + ":" + jenkinsResult.toLowerCase());
            }
            tagsPayload.add(prefix + CITags._NUMBER + ":" + buildData.getBuildNumber(""));

            // For backwards compat
            tagsPayload.add(prefix + CITags._RESULT + ":" + status);

            // Configurations
            final String fullJobName = buildData.getJobName("");
            final String gitRef = gitBranch != null ? gitBranch : gitTag;
            final Map<String, String> configurations = JobNameConfigurationParser.getConfigurations(fullJobName, gitRef);
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

            final String nodeName = getNodeName(run, buildData, updatedBuildData);
            nodePayload.put("name", nodeName);
            if(!DatadogUtilities.isMainNode(nodeName)) {

                final String workerHostname = getNodeHostname(run, updatedBuildData);

                // If the worker hostname is equals to controller hostname but the node name is not master/built-in then we
                // could not detect the worker hostname properly. Check if it's set in the environment, otherwise set to none.
                if(buildData.getHostname("").equalsIgnoreCase(workerHostname)) {
                    String envHostnameOrNone = DatadogUtilities.getHostnameFromWorkerEnv(run).orElse(HOSTNAME_NONE);
                    nodePayload.put("hostname", envHostnameOrNone);
                } else {
                    nodePayload.put("hostname", (workerHostname != null) ? workerHostname : HOSTNAME_NONE);
                }
            } else {
                nodePayload.put("hostname", DatadogUtilities.getHostname(null));
            }

            final String workspace = buildData.getWorkspace("").isEmpty() ? updatedBuildData.getWorkspace("") : buildData.getWorkspace("");
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

            final String gitCommit = buildData.getGitCommit("").isEmpty() ? updatedBuildData.getGitCommit("") : buildData.getGitCommit("");
            if(!isValidCommit(gitCommit)) {
                logger.warning("Couldn't find a valid commit for pipelineID '"+buildData.getBuildTag("")+"'. GIT_COMMIT environment variable was not found or has invalid SHA1 string: " + gitCommit);
            } else {
                gitPayload.put("sha", gitCommit);
            }

            final String gitRepoUrl = buildData.getGitUrl("").isEmpty() ? updatedBuildData.getGitUrl("") : buildData.getGitUrl("");
            if (gitRepoUrl != null && !gitRepoUrl.isEmpty()) {
                gitPayload.put("repository_url", filterSensitiveInfo(gitRepoUrl));
            }

            final String gitMessage = buildData.getGitMessage("").isEmpty() ? updatedBuildData.getGitMessage("") : buildData.getGitMessage("");
            if (gitMessage != null && !gitMessage.isEmpty()) {
                gitPayload.put("message", gitMessage);
            }

            final String gitAuthorDate = buildData.getGitAuthorDate("").isEmpty() ? updatedBuildData.getGitAuthorDate("") : buildData.getGitAuthorDate("");
            if (gitAuthorDate != null && !gitAuthorDate.isEmpty()) {
                gitPayload.put("author_time", gitAuthorDate);
            }

            final String gitCommitDate = buildData.getGitCommitterDate("").isEmpty() ? updatedBuildData.getGitCommitterDate("") : buildData.getGitCommitterDate("");
            if (gitCommitDate != null && !gitCommitDate.isEmpty()) {
                gitPayload.put("commit_time", gitCommitDate);
            }

            final String gitCommitterName = buildData.getGitCommitterName("").isEmpty() ? updatedBuildData.getGitCommitterName("") : buildData.getGitCommitterName("");
            if (gitCommitterName != null && !gitCommitterName.isEmpty()) {
                gitPayload.put("committer_name", gitCommitterName);
            }

            final String gitCommitterEmail = buildData.getGitCommitterEmail("").isEmpty() ? updatedBuildData.getGitCommitterEmail("") : buildData.getGitCommitterEmail("");
            if (gitCommitterEmail != null && !gitCommitterEmail.isEmpty()) {
                gitPayload.put("committer_email", gitCommitterEmail);
            }

            final String gitAuthorName = buildData.getGitAuthorName("").isEmpty() ? updatedBuildData.getGitAuthorName("") : buildData.getGitAuthorName("");
            if (gitAuthorName != null && !gitAuthorName.isEmpty()) {
                gitPayload.put("author_name", gitAuthorName);
            }

            final String gitAuthorEmail = buildData.getGitAuthorEmail("").isEmpty() ? updatedBuildData.getGitAuthorEmail("") : buildData.getGitAuthorEmail("");
            if (gitAuthorEmail != null && !gitAuthorEmail.isEmpty()) {
                gitPayload.put("author_email", gitAuthorEmail);
            }

            final String gitDefaultBranch = buildData.getGitDefaultBranch("").isEmpty() ? updatedBuildData.getGitDefaultBranch("") : buildData.getGitDefaultBranch("");
            if (gitDefaultBranch != null && !gitDefaultBranch.isEmpty()) {
                gitPayload.put("default_branch", gitDefaultBranch);
            }

            payload.put("git", gitPayload);
        }

        return payload;
    }

}
