package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommitSha;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.normalizeTag;

import hudson.model.Run;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;
import org.datadog.jenkins.plugins.datadog.model.Status;
import org.datadog.jenkins.plugins.datadog.model.git.GitCommitMetadata;
import org.datadog.jenkins.plugins.datadog.model.git.GitMetadata;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

/**
 * Keeps the logic to send webhooks related to inner jobs of Jenkins Pipelines (datadog levels: stage and job).
 * The top-level job (datadog level: pipeline) is handled by DatadogWebhookBuildLogic
 */
public class DatadogWebhookPipelineLogic extends DatadogBasePipelineLogic {

    @Nonnull
    @Override
    public JSONObject toJson(PipelineStepData current, Run<?, ?> run) throws IOException, InterruptedException {
        BuildData buildData = BuildData.create(run, DatadogUtilities.getTaskListener(run));

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

        payload.put("start", DatadogUtilities.toISO8601(new Date(current.getStartTimeMillis())));
        payload.put("end", DatadogUtilities.toISO8601(new Date(current.getEndTimeMillis())));

        payload.put("queue_time", current.getQueueTimeMillis());

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

        Map<String, String> gitPayload = createGitPayload(buildData.getGitMetadata());
        if (!gitPayload.isEmpty()) {
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
            globalTags.putAll(TagsUtil.convertTagsToMapSingleValues(current.getTags()));

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

            final String executorNumber = current.getExecutorNumber();
            if (StringUtils.isNotEmpty(executorNumber)) {
                tagsPayload.add(CITags.JENKINS_EXECUTOR_NUMBER  + ":" + executorNumber);
            }

            Map<String, String> pipelineDefinitionGitPayload = createGitPayload(buildData.getPipelineDefinitionGitMetadata());
            for (Map.Entry<String, String> e : pipelineDefinitionGitPayload.entrySet()) {
                tagsPayload.add(CITags.PIPELINE_DEFINITION_GIT + "." + e.getKey() + ":" + e.getValue());
            }

            payload.put("tags", tagsPayload);
        }

        return payload;
    }

    private static Map<String, String> createGitPayload(GitMetadata gitMetadata) {
        Map<String, String> payload = new HashMap<>();

        String repoUrl = gitMetadata.getRepositoryURL();
        if (repoUrl != null && !repoUrl.isEmpty()) {
            payload.put("repository_url", filterSensitiveInfo(repoUrl));
        }

        String defaultBranch = gitMetadata.getDefaultBranch();
        if (defaultBranch != null && !defaultBranch.isEmpty()) {
            payload.put("default_branch", defaultBranch);
        }

        String branch = gitMetadata.getBranch();
        if(StringUtils.isNotEmpty(branch)) {
            payload.put("branch", branch);
        }

        GitCommitMetadata commitMetadata = gitMetadata.getCommitMetadata();

        String commit = commitMetadata.getCommit();
        if (isValidCommitSha(commit)) {
            payload.put("sha", commit);
        }

        String tag = commitMetadata.getTag();
        if(StringUtils.isNotEmpty(tag)) {
            payload.put("tag", tag);
        }

        String message = commitMetadata.getMessage();
        if (message != null && !message.isEmpty()) {
            payload.put("message", message);
        }

        String authorDate = commitMetadata.getAuthorDate();
        if (authorDate != null && !authorDate.isEmpty()) {
            payload.put("author_time", authorDate);
        }

        String commitDate = commitMetadata.getCommitterDate();
        if (commitDate != null && !commitDate.isEmpty()) {
            payload.put("commit_time", commitDate);
        }

        String committerName = commitMetadata.getCommitterName();
        if (committerName != null && !committerName.isEmpty()) {
            payload.put("committer_name", committerName);
        }

        String committerEmail = commitMetadata.getCommitterEmail();
        if (committerEmail != null && !committerEmail.isEmpty()) {
            payload.put("committer_email", committerEmail);
        }

        String authorName = commitMetadata.getAuthorName();
        if (authorName != null && !authorName.isEmpty()) {
            payload.put("author_name", authorName);
        }

        String authorEmail = commitMetadata.getAuthorEmail();
        if (authorEmail != null && !authorEmail.isEmpty()) {
            payload.put("author_email", authorEmail);
        }

        return payload;
    }

}
