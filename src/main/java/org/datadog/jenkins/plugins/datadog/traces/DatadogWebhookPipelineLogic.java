package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;

import hudson.model.Run;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;
import org.datadog.jenkins.plugins.datadog.model.Status;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

/**
 * Keeps the logic to send webhooks related to inner jobs of Jenkins Pipelines (datadog levels: stage and job).
 * The top-level job (datadog level: pipeline) is handled by DatadogWebhookBuildLogic
 */
public class DatadogWebhookPipelineLogic extends DatadogBasePipelineLogic {

    @Nonnull
    @Override
    public JSONObject toJson(PipelineStepData current, Run<?, ?> run) throws IOException, InterruptedException {
        BuildData buildData = new BuildData(run, DatadogUtilities.getTaskListener(run));

        JSONObject payload = new JSONObject();
        payload.put("partial_retry", false);

        long traceId = current.getTraceId();
        payload.put("trace_id", traceId);

        long parentSpanId = current.getParentSpanId();
        payload.put("parent_span_id", parentSpanId);

        long spanId = current.getSpanId();
        payload.put("span_id", spanId);

        payload.put("id", current.getId());
        payload.put("name", current.getName());

        final String buildLevel = current.getType().getBuildLevel();
        payload.put("level", buildLevel);

        // If the root has propagated queue time, we need to adjust all startTime and endTime from Jenkins pipelines
        // because this time will be subtracted in the root. See DatadogTraceBuildLogic#finishBuildTrace method.
        final long propagatedMillisInQueue = Math.max(buildData.getPropagatedMillisInQueue(-1L), 0);

        final long fixedStartTimeMillis = TimeUnit.MICROSECONDS.toMillis(current.getStartTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue));
        payload.put("start", DatadogUtilities.toISO8601(new Date(fixedStartTimeMillis)));

        final long fixedEndTimeMillis = TimeUnit.MICROSECONDS.toMillis(current.getEndTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue));
        payload.put("end", DatadogUtilities.toISO8601(new Date(fixedEndTimeMillis)));

        payload.put("queue_time", TimeUnit.NANOSECONDS.toMillis(current.getNanosInQueue()));

        Status status = current.getStatus();
        payload.put("status", status.toTag());

        payload.put("pipeline_unique_id", buildData.getBuildTag(""));
        payload.put("pipeline_name", buildData.getJobName());

        String url = buildData.getBuildUrl("");
        if (StringUtils.isNotBlank(url)) {
            payload.put("url", url + "execution/node/" + current.getId() + "/");
        }

        if (buildLevel.equals("stage")) {
            String parentStageId = current.getStageId();
            if (parentStageId != null) {
                // Stage is a child of another stage
                payload.put("parent_stage_id", parentStageId);
            }
        } else if (buildLevel.equals("job")) {
            payload.put("stage_id", current.getStageId());
            payload.put("stage_name", current.getStageName());
        }

        // Errors
        if (current.isError() && current.getErrorObj() != null) {
            JSONObject errPayload = new JSONObject();
            final Throwable error = current.getErrorObj();
            errPayload.put("message", error.getMessage());
            errPayload.put("type", error.getClass().getName());
            errPayload.put("domain", "unknown");
            final StringWriter errorString = new StringWriter();
            error.printStackTrace(new PrintWriter(errorString));
            errPayload.put("stack", errorString.toString());

            payload.put("error", errPayload);
        } else if (current.isUnstable() && current.getUnstableMessage() != null) {
            JSONObject errPayload = new JSONObject();
            errPayload.put("message", current.getUnstableMessage());
            errPayload.put("type", "unstable");
            errPayload.put("domain", "unknown");
            payload.put("error", errPayload);
        }

        // Node
        {
            JSONObject nodePayload = new JSONObject();

            final String nodeName = getNodeName(current, buildData);
            nodePayload.put("name", nodeName);

            if (!DatadogUtilities.isMainNode(nodeName)) {
                final String workerHostname = getNodeHostname(current, buildData);
                nodePayload.put("hostname", (workerHostname != null) ? workerHostname : HOSTNAME_NONE);
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

            String rawGitBranch = buildData.getBranch("");
            String gitBranch;
            String gitTag;
            if (rawGitBranch != null && !rawGitBranch.isEmpty()) {
                gitBranch = normalizeBranch(rawGitBranch);
                if (gitBranch != null) {
                    gitPayload.put("branch", gitBranch);
                }
                gitTag = normalizeTag(rawGitBranch);
                if (gitTag != null) {
                    gitPayload.put("tag", gitTag);
                }
            }

            // If the user set DD_GIT_TAG manually,
            // we override the git.tag value.
            gitTag = buildData.getGitTag("");
            if (StringUtils.isNotEmpty(gitTag)) {
                gitPayload.put("tag", gitTag);
            }

            // If we could detect a valid commit, the buildData object will contain that commit.
            // If we could not detect a valid commit, that means that the GIT_COMMIT environment variable
            // was overridden by the user at top level, so we set the content what we have (despite it's not valid).
            // We will show a logger.warning at the end of the pipeline.
            String gitCommit = buildData.getGitCommit("");
            if (gitCommit != null && !gitCommit.isEmpty()) {
                gitPayload.put("sha", gitCommit);
            }

            String gitRepoUrl = buildData.getGitUrl("");
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

        // User
        {
            JSONObject userPayload = new JSONObject();
            String user = buildData.getUserId();
            userPayload.put("name", user);
            if (StringUtils.isNotEmpty(buildData.getUserEmail(""))) {
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

            for (Map.Entry<String, String> globalTagEntry : globalTags.entrySet()) {
                tagsPayload.add(globalTagEntry.getKey() + ":" + globalTagEntry.getValue());
            }

            // Jenkins specific
            tagsPayload.add(CITags._DD_CI_INTERNAL + ":false");

            String jenkinsResult = current.getJenkinsResult();
            if (StringUtils.isNotEmpty(jenkinsResult)) {
                tagsPayload.add(CITags.JENKINS_RESULT + ":" + jenkinsResult.toLowerCase());
            }

            final String prefix = current.getType().getTagName();

            // For backwards compat
            tagsPayload.add(prefix + CITags._RESULT + ":" + status.toTag());

            // Arguments
            final String nodePrefix = current.getType().name().toLowerCase();
            for (Map.Entry<String, Object> entry : current.getArgs().entrySet()) {
                tagsPayload.add(CI_PROVIDER + "." + nodePrefix + ".args." + entry.getKey() + ":" + entry.getValue());
                if ("script".equals(entry.getKey())) {
                    tagsPayload.add(prefix + ".script" + ":" + entry.getValue());
                }
            }

            payload.put("tags", tagsPayload);
        }

        return payload;
    }

}
