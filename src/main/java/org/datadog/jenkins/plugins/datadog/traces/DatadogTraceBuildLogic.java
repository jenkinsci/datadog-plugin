package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getNormalizedResultForTraces;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import datadog.trace.api.DDTags;
import hudson.model.Result;
import hudson.model.Run;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.QueueInfoAction;
import org.datadog.jenkins.plugins.datadog.model.StageBreakdownAction;
import org.datadog.jenkins.plugins.datadog.model.StageData;
import org.datadog.jenkins.plugins.datadog.model.TimeInQueueAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Keeps the logic to send traces related to Jenkins Build.
 */
public class DatadogTraceBuildLogic {

    private static final Logger logger = Logger.getLogger(DatadogTraceBuildLogic.class.getName());

    private final Tracer tracer;
    private final Gson gson;

    public DatadogTraceBuildLogic(final Tracer tracer) {
        this.tracer = tracer;
        this.gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    }

    public void startBuildTrace(final BuildData buildData, Run run) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildTraces()) {
            logger.fine("Trace Collection disabled");
            return;
        }

        // Traces
        if(this.tracer == null) {
            logger.severe("Unable to send build traces. Tracer is null");
            return;
        }

        final long startTimeMicros = buildData.getStartTime(0L) * 1000;

        final Span buildSpan = tracer.buildSpan("jenkins.build")
                .withStartTimestamp(startTimeMicros)
                .start();

        getBuildSpanManager().put(buildData.getBuildTag(""), buildSpan);

        // The buildData object is stored in the BuildSpanAction to be updated
        // by the information that will be calculated when the pipeline listeners
        // were executed. This is needed because if the user build is based on
        // Jenkins Pipelines, there are many information that is missing when the
        // root span is created, such as Git info (this is calculated in an inner step
        // of the pipeline)
        final BuildSpanAction buildSpanAction = new BuildSpanAction(buildData);
        this.tracer.inject(buildSpan.context(), Format.Builtin.TEXT_MAP, new BuildTextMapAdapter(buildSpanAction.getBuildSpanPropatation()));
        run.addAction(buildSpanAction);

        final StepDataAction stepDataAction = new StepDataAction();
        run.addAction(stepDataAction);

        final StageBreakdownAction stageBreakdownAction = new StageBreakdownAction();
        run.addAction(stageBreakdownAction);

        final QueueInfoAction queueInfoAction = new QueueInfoAction();
        run.addAction(queueInfoAction);
    }

    public void finishBuildTrace(final BuildData buildData, final Run<?,?> run) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildTraces()) {
            return;
        }

        // APM Traces
        final Span buildSpan = getBuildSpanManager().remove(buildData.getBuildTag(""));
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
        final BuildData pipelineData = buildSpanAction.getBuildData();

        final String prefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();
        final String buildLevel = BuildPipelineNode.NodeType.PIPELINE.getBuildLevel();
        final long endTimeMicros = buildData.getEndTime(0L) * 1000;
        buildSpan.setTag(DDTags.SERVICE_NAME, DatadogUtilities.getDatadogGlobalDescriptor().getTraceServiceName());
        buildSpan.setTag(DDTags.SPAN_TYPE, "ci");
        buildSpan.setTag(CITags.CI_PROVIDER_NAME, "jenkins");
        buildSpan.setTag(DDTags.LANGUAGE_TAG_KEY, "");
        buildSpan.setTag(CITags._DD_CI_INTERNAL, false);
        buildSpan.setTag(CITags._DD_CI_BUILD_LEVEL, buildLevel);
        buildSpan.setTag(CITags.USER_NAME, buildData.getUserId());
        buildSpan.setTag(prefix + CITags._ID, buildData.getBuildTag(""));
        buildSpan.setTag(prefix + CITags._NUMBER, buildData.getBuildNumber(""));
        buildSpan.setTag(prefix + CITags._URL, buildData.getBuildUrl(""));

        final TimeInQueueAction timeInQueueAction = run.getAction(TimeInQueueAction.class);
        if(timeInQueueAction != null) {
            buildSpan.setTag(CITags.QUEUE_TIME, Math.max(timeInQueueAction.getSecondsInQueue(), 0));
        }

        final String workspace = buildData.getWorkspace("").isEmpty() ? pipelineData.getWorkspace("") : buildData.getWorkspace("");
        buildSpan.setTag(CITags.WORKSPACE_PATH, workspace);

        final String nodeName = buildData.getNodeName("").isEmpty() ? pipelineData.getNodeName("") : buildData.getNodeName("");
        buildSpan.setTag(CITags.NODE_NAME, nodeName);

        final String nodeHostname = buildData.getHostname("").isEmpty() ? pipelineData.getHostname("") : buildData.getHostname("");
        buildSpan.setTag(CITags._DD_HOSTNAME, nodeHostname);

        // Git Info
        final String gitUrl = buildData.getGitUrl("").isEmpty() ? pipelineData.getGitUrl("") : buildData.getGitUrl("");
        buildSpan.setTag(CITags.GIT_REPOSITORY_URL, gitUrl);

        final String gitCommit = buildData.getGitCommit("").isEmpty() ? pipelineData.getGitCommit("") : buildData.getGitCommit("");
        buildSpan.setTag(CITags.GIT_COMMIT__SHA, gitCommit); //Maintain retrocompatibility
        buildSpan.setTag(CITags.GIT_COMMIT_SHA, gitCommit);

        final String gitMessage = buildData.getGitMessage("").isEmpty() ? pipelineData.getGitMessage("") : buildData.getGitMessage("");
        buildSpan.setTag(CITags.GIT_COMMIT_MESSAGE, gitMessage);

        final String gitAuthor = buildData.getGitAuthorName("").isEmpty() ? pipelineData.getGitAuthorName("") : buildData.getGitAuthorName("");
        buildSpan.setTag(CITags.GIT_COMMIT_AUTHOR_NAME, gitAuthor);

        final String gitAuthorEmail = buildData.getGitAuthorEmail("").isEmpty() ? pipelineData.getGitAuthorEmail("") : buildData.getGitAuthorEmail("");
        buildSpan.setTag(CITags.GIT_COMMIT_AUTHOR_EMAIL, gitAuthorEmail);

        final String gitAuthorDate = buildData.getGitAuthorDate("").isEmpty() ? pipelineData.getGitAuthorDate("") : buildData.getGitAuthorDate("");
        buildSpan.setTag(CITags.GIT_COMMIT_AUTHOR_DATE, gitAuthorDate);

        final String gitCommitter = buildData.getGitCommitterName("").isEmpty() ? pipelineData.getGitCommitterName("") : buildData.getGitCommitterName("");
        buildSpan.setTag(CITags.GIT_COMMIT_COMMITTER_NAME, gitCommitter);

        final String gitCommitterEmail = buildData.getGitCommitterEmail("").isEmpty() ? pipelineData.getGitCommitterEmail("") : buildData.getGitCommitterEmail("");
        buildSpan.setTag(CITags.GIT_COMMIT_COMMITTER_EMAIL, gitCommitterEmail);

        final String gitCommitterDate = buildData.getGitCommitterDate("").isEmpty() ? pipelineData.getGitCommitterDate("") : buildData.getGitCommitterDate("");
        buildSpan.setTag(CITags.GIT_COMMIT_COMMITTER_DATE, gitCommitterDate);

        final String gitDefaultBranch = buildData.getGitDefaultBranch("").isEmpty() ? pipelineData.getGitDefaultBranch("") : buildData.getGitDefaultBranch("");
        buildSpan.setTag(CITags.GIT_DEFAULT_BRANCH, gitDefaultBranch);

        final String rawGitBranch = buildData.getBranch("").isEmpty() ? pipelineData.getBranch("") : buildData.getBranch("");
        final String gitBranch = normalizeBranch(rawGitBranch);
        if(gitBranch != null) {
            buildSpan.setTag(CITags.GIT_BRANCH, gitBranch);
        }

        final String gitTag = normalizeTag(rawGitBranch);
        if(gitTag != null) {
            buildSpan.setTag(CITags.GIT_TAG, gitTag);
        }

        final JobNameWrapper jobNameWrapper = new JobNameWrapper(buildData.getJobName(""), gitBranch != null ? gitBranch : gitTag);
        buildSpan.setTag(DDTags.RESOURCE_NAME, jobNameWrapper.getTraceJobName());
        buildSpan.setTag(prefix + CITags._NAME, jobNameWrapper.getTraceJobName());

        if(!jobNameWrapper.getConfigurations().isEmpty()){
            for(Map.Entry<String, String> entry : jobNameWrapper.getConfigurations().entrySet()) {
                buildSpan.setTag(prefix + CITags._CONFIGURATION + "." + entry.getKey(), entry.getValue());
            }
        }

        // Stage breakdown
        final StageBreakdownAction stageBreakdownAction = run.getAction(StageBreakdownAction.class);
        if(stageBreakdownAction != null){
            final Map<String, StageData> stageDataByName = stageBreakdownAction.getStageDataByName();
            final List<StageData> stages = new ArrayList<>(stageDataByName.values());
            Collections.sort(stages);

            final String stagesJson = gson.toJson(stages);
            buildSpan.setTag(CITags._DD_CI_STAGES, stagesJson);
        }

        // Jenkins specific
        buildSpan.setTag(CITags.JENKINS_TAG, buildData.getBuildTag(""));
        buildSpan.setTag(CITags.JENKINS_EXECUTOR_NUMBER, buildData.getExecutorNumber(""));

        final String jenkinsResult = buildData.getResult("");
        final String pipelineResult = getNormalizedResultForTraces(Result.fromString(jenkinsResult));
        buildSpan.setTag(prefix + CITags._RESULT, pipelineResult);
        buildSpan.setTag(CITags.JENKINS_RESULT, jenkinsResult.toLowerCase());
        if(Result.FAILURE.toString().equals(jenkinsResult)) {
            buildSpan.setTag(CITags.ERROR, true);
        }

        buildSpan.finish(endTimeMicros);
    }

    protected BuildSpanManager getBuildSpanManager() {
        return BuildSpanManager.get();
    }
}
