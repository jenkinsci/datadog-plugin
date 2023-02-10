package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.cleanUpTraceActions;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.statusFromResult;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.toJson;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.filterSensitiveInfo;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.CIGlobalTagsAction;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.model.Run;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Keeps the logic to send webhooks related to inner jobs of Jenkins Pipelines (datadog levels: stage and job).
 * The top-level job (datadog level: pipeline) is handled by DatadogWebhookBuildLogic
 */
public class DatadogWebhookPipelineLogic extends DatadogBasePipelineLogic {

    private final DatadogClient client;

    public DatadogWebhookPipelineLogic(final DatadogClient client) {
        this.client = client;
    }

    @Override
    public void execute(Run run, FlowNode flowNode) {

        if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
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
        collectTraces(run, buildData, root, null, traceSpanContext);

        // Explicit removal of InvisibleActions used to collect Traces when the Run finishes.
        cleanUpTraceActions(run);
    }

    private void collectTraces(final Run run, final BuildData buildData, final BuildPipelineNode current, final BuildPipelineNode parent, final TraceSpan.TraceSpanContext parentSpanContext) {

        if(!isTraceable(current)) {
            // If the current node is not traceable, we continue with its children
            for(final BuildPipelineNode child : current.getChildren()) {
                collectTraces(run, buildData, child, parent, parentSpanContext);
            }
            return;
        }
        // If the root has propagated queue time, we need to adjust all startTime and endTime from Jenkins pipelines
        // because this time will be subtracted in the root. See DatadogTraceBuildLogic#finishBuildTrace method.
        final long propagatedMillisInQueue = Math.max(buildData.getPropagatedMillisInQueue(-1L), 0);
        final long fixedStartTimeMillis = TimeUnit.MICROSECONDS.toMillis(current.getStartTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue));
        final long fixedEndTimeMillis = TimeUnit.MICROSECONDS.toMillis(current.getEndTimeMicros() - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue));
        final String jenkinsResult = getResult(current);
        final String status = statusFromResult(jenkinsResult);
        final String prefix = current.getType().getTagName();
        final String buildLevel = current.getType().getBuildLevel();

        final TraceSpan.TraceSpanContext spanContext = new TraceSpan.TraceSpanContext(parentSpanContext.getTraceId(), parentSpanContext.getSpanId(), current.getSpanId());
        final TraceSpan span = new TraceSpan(buildOperationName(current), TimeUnit.MILLISECONDS.toNanos(fixedStartTimeMillis + propagatedMillisInQueue), spanContext);

        final Map<String, String> envVars = current.getEnvVars();

        JSONObject payload = new JSONObject();
        payload.put("level", buildLevel);
        final String url = envVars.get("BUILD_URL") != null ? envVars.get("BUILD_URL") : buildData.getBuildUrl("");
        if(StringUtils.isNotBlank(url)) {
            payload.put("url", url + "execution/node/"+current.getId()+"/");
        }
        payload.put("start", DatadogUtilities.toISO8601(new Date(fixedStartTimeMillis)));
        payload.put("end", DatadogUtilities.toISO8601(new Date(fixedEndTimeMillis)));
        payload.put("partial_retry", false);
        payload.put("queue_time", TimeUnit.NANOSECONDS.toMillis(getNanosInQueue(current)));
        payload.put("status", status);

        payload.put("trace_id", spanContext.getTraceId());
        payload.put("span_id", spanContext.getSpanId());
        payload.put("parent_span_id", spanContext.getParentId());

        payload.put("id", current.getId());
        payload.put("name", current.getName());

        payload.put("pipeline_unique_id", buildData.getBuildTag(""));
        payload.put("pipeline_name", buildData.getBaseJobName(""));
        if (buildLevel.equals("stage")) {
            if (parent != null && parent.getType().getBuildLevel() == "stage") {
                // Stage is a child of another stage
                payload.put("parent_stage_id", parent.getStageId());
            }
        } else if (buildLevel.equals("job")) {
            payload.put("stage_id", current.getStageId());
            payload.put("stage_name", current.getStageName());
        }

        // Errors
        if(current.isError() && current.getErrorObj() != null) {

            JSONObject errPayload = new JSONObject();

            final Throwable error = current.getErrorObj();
            errPayload.put("message", error.getMessage());
            errPayload.put("type", error.getClass().getName());
            errPayload.put("domain", "unknown");
            final StringWriter errorString = new StringWriter();
            error.printStackTrace(new PrintWriter(errorString));
            errPayload.put("stack", errorString.toString());

            payload.put("error", errPayload);
        }

        // Node
        {
            JSONObject nodePayload = new JSONObject();

            final String nodeName = getNodeName(run, current, buildData);
            nodePayload.put("name", nodeName);

            if(!DatadogUtilities.isMainNode(nodeName)) {
                final String workerHostname = getNodeHostname(run, current);
                // If the worker hostname is equals to controller hostname but the node name is not "master"
                // then we could not detect the worker hostname properly. We set _dd.hostname to 'none' explicitly.
                if(buildData.getHostname("").equalsIgnoreCase(workerHostname)) {
                    nodePayload.put("hostname", HOSTNAME_NONE);
                } else {
                    nodePayload.put("hostname", (workerHostname != null) ? workerHostname : HOSTNAME_NONE);
                }
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

            final String rawGitBranch = GitUtils.resolveGitBranch(envVars, buildData);
            String gitBranch = null;
            String gitTag = null;
            if(rawGitBranch != null && !rawGitBranch.isEmpty()) {
                gitBranch = normalizeBranch(rawGitBranch);
                if(gitBranch != null) {
                    gitPayload.put("branch", gitBranch);
                }
                gitTag = normalizeTag(rawGitBranch);
                if(gitTag != null) {
                    gitPayload.put("tag", gitTag);
                }
            }

            // If the user set DD_GIT_TAG manually,
            // we override the git.tag value.
            gitTag = GitUtils.resolveGitTag(envVars, buildData);
            if(StringUtils.isNotEmpty(gitTag)){
                gitPayload.put("tag", gitTag);
            }

            // If we could detect a valid commit, the buildData object will contain that commit.
            // If we could not detect a valid commit, that means that the GIT_COMMIT environment variable
            // was overridden by the user at top level, so we set the content what we have (despite it's not valid).
            // We will show a logger.warning at the end of the pipeline.
            final String gitCommit = GitUtils.resolveGitCommit(envVars, buildData);
            if(gitCommit != null && !gitCommit.isEmpty()) {
                gitPayload.put("sha", gitCommit);
            }

            final String gitRepoUrl = GitUtils.resolveGitRepositoryUrl(envVars, buildData);
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

        // Tags
        // Here we include both global tags and fields that are not supported as regular fields by the webhooks intake
        {
            JSONArray tagsPayload = new JSONArray();

            final CIGlobalTagsAction ciGlobalTagsAction = run.getAction(CIGlobalTagsAction.class);
            if(ciGlobalTagsAction != null) {
                final Map<String, String> globalTags = ciGlobalTagsAction.getTags();
                for(Map.Entry<String, String> globalTagEntry : globalTags.entrySet()) {
                    tagsPayload.add(globalTagEntry.getKey() + ":" + globalTagEntry.getValue());
                }

            }

            // Jenkins specific
            tagsPayload.add(CITags._DD_CI_INTERNAL + ":" + current.isInternal());
            if (StringUtils.isNotEmpty(jenkinsResult)) {
                tagsPayload.add(CITags.JENKINS_RESULT + ":" + jenkinsResult.toLowerCase());
            }

            // For backwards compat
            tagsPayload.add(prefix + CITags._RESULT + ":" + status);

            // User
            final String user = envVars.get("USER") != null ? envVars.get("USER") : buildData.getUserId();
            tagsPayload.add(CITags.USER_NAME + ":" + user);

            // Pipeline Parameters
            if(!buildData.getBuildParameters().isEmpty()) {
                tagsPayload.add("parameters" + ":" + toJson(buildData.getBuildParameters()));
            }

            // Arguments
            final String nodePrefix = current.getType().name().toLowerCase();
            for(Map.Entry<String, Object> entry : current.getArgs().entrySet()) {
                tagsPayload.add(CI_PROVIDER + "." + nodePrefix + ".args."+entry.getKey() + ":" + String.valueOf(entry.getValue()));
                if("script".equals(entry.getKey())){
                    tagsPayload.add(prefix + ".script" + ":" + String.valueOf(entry.getValue()));
                }
            }

            payload.put("tags", tagsPayload);
        }


        for(final BuildPipelineNode child : current.getChildren()) {
            collectTraces(run, buildData, child, current, span.context());
        }

        client.postWebhook(payload.toString());
    }

}
