package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getNormalizedResultForTraces;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.toJson;
import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;

import datadog.trace.api.DDTags;
import datadog.trace.api.interceptor.MutableSpan;
import hudson.model.Result;
import hudson.model.Run;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.CIGlobalTagsAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineQueueInfoAction;
import org.datadog.jenkins.plugins.datadog.model.StageBreakdownAction;
import org.datadog.jenkins.plugins.datadog.model.StageData;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.json.JsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Keeps the logic to send traces related to Jenkins Build.
 */
public class DatadogTraceBuildLogic {

    private static final String HOSTNAME_NONE = "none";
    private static final Logger logger = Logger.getLogger(DatadogTraceBuildLogic.class.getName());

    private final Tracer tracer;

    public DatadogTraceBuildLogic(final Tracer tracer) {
        this.tracer = tracer;
    }

    public void startBuildTrace(final BuildData buildData, Run run) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isEnabledCiVisibility()) {
            logger.fine("CI Visibility is disabled");
            return;
        }

        // Traces
        if(this.tracer == null) {
            logger.severe("Unable to send build traces. Tracer is null");
            return;
        }

        final long startTimeMicros = buildData.getStartTime(0L) * 1000;

        //TODO: Remove Java Tracer
        final Span buildSpanOld = tracer.buildSpan("jenkins.build")
                .withStartTimestamp(startTimeMicros)
                .start();

        final TraceSpan buildSpan = new TraceSpan("jenkins.build", TimeUnit.MILLISECONDS.toNanos(buildData.getStartTime(0L)));

        //TODO: Remove Java Tracer
        getBuildSpanManager().putOld(buildData.getBuildTag(""), buildSpanOld);

        getBuildSpanManager().put(buildData.getBuildTag(""), buildSpan);


        // The buildData object is stored in the BuildSpanAction to be updated
        // by the information that will be calculated when the pipeline listeners
        // were executed. This is needed because if the user build is based on
        // Jenkins Pipelines, there are many information that is missing when the
        // root span is created, such as Git info (this is calculated in an inner step
        // of the pipeline)
        final BuildSpanAction buildSpanAction = new BuildSpanAction(buildData, buildSpan.getTraceId(), buildSpan.getSpanId());

        // TODO: Remove Java Tracer
        this.tracer.inject(buildSpanOld.context(), Format.Builtin.TEXT_MAP, new BuildTextMapAdapter(buildSpanAction.getBuildSpanPropatationOld()));
        run.addAction(buildSpanAction);

        final StepDataAction stepDataAction = new StepDataAction();
        run.addAction(stepDataAction);

        final StageBreakdownAction stageBreakdownAction = new StageBreakdownAction();
        run.addAction(stageBreakdownAction);

        final PipelineQueueInfoAction pipelineQueueInfoAction = new PipelineQueueInfoAction();
        run.addAction(pipelineQueueInfoAction);

        final CIGlobalTagsAction ciGlobalTags = new CIGlobalTagsAction(buildData.getTagsForTraces());
        run.addAction(ciGlobalTags);
    }

    public void finishBuildTrace(final BuildData buildData, final Run<?,?> run) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isEnabledCiVisibility()) {
            return;
        }

        // APM Traces
        //TODO: Remove Java Tracer
        final Span buildSpanOld = getBuildSpanManager().removeOld(buildData.getBuildTag(""));
        if(buildSpanOld == null) {
            return;
        }

        final TraceSpan buildSpan = getBuildSpanManager().remove(buildData.getBuildTag(""));
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

        //TODO Remove Java Tracer
        buildSpanOld.setTag(DDTags.SERVICE_NAME, DatadogUtilities.getDatadogGlobalDescriptor().getCiInstanceName());
        buildSpan.setService(DatadogUtilities.getDatadogGlobalDescriptor().getCiInstanceName());

        //TODO Remove Java Tracer
        buildSpanOld.setTag(DDTags.SPAN_TYPE, "ci");
        buildSpan.setType("ci");

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.CI_PROVIDER_NAME, "jenkins");
        buildSpan.putMeta(CITags.CI_PROVIDER_NAME, "jenkins");

        //TODO Remove Java Tracer
        buildSpanOld.setTag(DDTags.LANGUAGE_TAG_KEY, "");
        buildSpan.putMeta(CITags.LANGUAGE_TAG_KEY, "");

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags._DD_CI_INTERNAL, false);
        buildSpan.putMeta(CITags._DD_CI_INTERNAL, "false");

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags._DD_CI_BUILD_LEVEL, buildLevel);
        buildSpan.putMeta(CITags._DD_CI_BUILD_LEVEL, buildLevel);

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags._DD_CI_LEVEL, buildLevel);
        buildSpan.putMeta(CITags._DD_CI_LEVEL, buildLevel);

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags._DD_ORIGIN, ORIGIN_CIAPP_PIPELINE);
        buildSpan.putMeta(CITags._DD_ORIGIN, ORIGIN_CIAPP_PIPELINE);

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.USER_NAME, buildData.getUserId());
        buildSpan.putMeta(CITags.USER_NAME, buildData.getUserId());

        //TODO Remove Java Tracer
        buildSpanOld.setTag(prefix + CITags._ID, buildData.getBuildTag(""));
        buildSpan.putMeta(prefix + CITags._ID, buildData.getBuildTag(""));

        //TODO Remove Java Tracer
        buildSpanOld.setTag(prefix + CITags._NUMBER, buildData.getBuildNumber(""));
        buildSpan.putMeta(prefix + CITags._NUMBER, buildData.getBuildNumber(""));

        //TODO Remove Java Tracer
        buildSpanOld.setTag(prefix + CITags._URL, buildData.getBuildUrl(""));
        buildSpan.putMeta(prefix + CITags._NUMBER, buildData.getBuildNumber(""));

        //TODO Remove Java Tracer
        if (buildSpanOld instanceof MutableSpan) {
            ((MutableSpan) buildSpanOld).setMetric(CITags.QUEUE_TIME, TimeUnit.MILLISECONDS.toSeconds(getMillisInQueue(updatedBuildData)));
        }
        buildSpan.putMetric(CITags.QUEUE_TIME, TimeUnit.MILLISECONDS.toSeconds(getMillisInQueue(updatedBuildData)));


        final String workspace = buildData.getWorkspace("").isEmpty() ? updatedBuildData.getWorkspace("") : buildData.getWorkspace("");
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.WORKSPACE_PATH, workspace);
        buildSpan.putMeta(CITags.WORKSPACE_PATH, workspace);

        final String nodeName = getNodeName(run, buildData, updatedBuildData);
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.NODE_NAME, nodeName);
        buildSpan.putMeta(CITags.NODE_NAME, nodeName);

        final String nodeLabelsJson = toJson(getNodeLabels(run, nodeName));
        if(!nodeLabelsJson.isEmpty()){
            //TODO Remove Java Tracer
            buildSpanOld.setTag(CITags.NODE_LABELS, nodeLabelsJson);
            buildSpan.putMeta(CITags.NODE_LABELS, nodeLabelsJson);
        }
        // If the NodeName == master, we don't set _dd.hostname. It will be overridden by the Datadog Agent. (Traces are only available using Datadog Agent)
        // If the NodeName != master, we set _dd.hostname to 'none' explicitly, cause we cannot calculate the worker hostname.
        if(!"master".equalsIgnoreCase(nodeName)) {
            //TODO Remove Java Tracer
            buildSpanOld.setTag(CITags._DD_HOSTNAME, HOSTNAME_NONE);
            buildSpan.putMeta(CITags._DD_HOSTNAME, HOSTNAME_NONE);
        }

        // Git Info
        final String gitUrl = buildData.getGitUrl("").isEmpty() ? updatedBuildData.getGitUrl("") : buildData.getGitUrl("");

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_REPOSITORY_URL, gitUrl);
        buildSpan.putMeta(CITags.GIT_REPOSITORY_URL, gitUrl);

        final String gitCommit = buildData.getGitCommit("").isEmpty() ? updatedBuildData.getGitCommit("") : buildData.getGitCommit("");
        if(!isValidCommit(gitCommit)) {
            logger.warning("Couldn't find a valid commit for pipelineID '"+buildData.getBuildTag("")+"'. GIT_COMMIT environment variable was not found or has invalid SHA1 string: " + gitCommit);
        }

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_COMMIT__SHA, gitCommit); //Maintain retrocompatibility
        buildSpan.putMeta(CITags.GIT_COMMIT__SHA, gitCommit); //Maintain retrocompatibility

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_COMMIT_SHA, gitCommit);
        buildSpan.putMeta(CITags.GIT_COMMIT_SHA, gitCommit);

        final String gitMessage = buildData.getGitMessage("").isEmpty() ? updatedBuildData.getGitMessage("") : buildData.getGitMessage("");
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_COMMIT_MESSAGE, gitMessage);
        buildSpan.putMeta(CITags.GIT_COMMIT_MESSAGE, gitMessage);

        final String gitAuthor = buildData.getGitAuthorName("").isEmpty() ? updatedBuildData.getGitAuthorName("") : buildData.getGitAuthorName("");
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_COMMIT_AUTHOR_NAME, gitAuthor);
        buildSpan.putMeta(CITags.GIT_COMMIT_AUTHOR_NAME, gitAuthor);

        final String gitAuthorEmail = buildData.getGitAuthorEmail("").isEmpty() ? updatedBuildData.getGitAuthorEmail("") : buildData.getGitAuthorEmail("");
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_COMMIT_AUTHOR_EMAIL, gitAuthorEmail);
        buildSpan.putMeta(CITags.GIT_COMMIT_AUTHOR_EMAIL, gitAuthorEmail);

        final String gitAuthorDate = buildData.getGitAuthorDate("").isEmpty() ? updatedBuildData.getGitAuthorDate("") : buildData.getGitAuthorDate("");
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_COMMIT_AUTHOR_DATE, gitAuthorDate);
        buildSpan.putMeta(CITags.GIT_COMMIT_AUTHOR_DATE, gitAuthorDate);

        final String gitCommitter = buildData.getGitCommitterName("").isEmpty() ? updatedBuildData.getGitCommitterName("") : buildData.getGitCommitterName("");
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_COMMIT_COMMITTER_NAME, gitCommitter);
        buildSpan.putMeta(CITags.GIT_COMMIT_COMMITTER_NAME, gitCommitter);

        final String gitCommitterEmail = buildData.getGitCommitterEmail("").isEmpty() ? updatedBuildData.getGitCommitterEmail("") : buildData.getGitCommitterEmail("");
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_COMMIT_COMMITTER_EMAIL, gitCommitterEmail);
        buildSpan.putMeta(CITags.GIT_COMMIT_COMMITTER_EMAIL, gitCommitterEmail);

        final String gitCommitterDate = buildData.getGitCommitterDate("").isEmpty() ? updatedBuildData.getGitCommitterDate("") : buildData.getGitCommitterDate("");
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_COMMIT_COMMITTER_DATE, gitCommitterDate);
        buildSpan.putMeta(CITags.GIT_COMMIT_COMMITTER_DATE, gitCommitterDate);

        final String gitDefaultBranch = buildData.getGitDefaultBranch("").isEmpty() ? updatedBuildData.getGitDefaultBranch("") : buildData.getGitDefaultBranch("");
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.GIT_DEFAULT_BRANCH, gitDefaultBranch);
        buildSpan.putMeta(CITags.GIT_DEFAULT_BRANCH, gitDefaultBranch);

        final String rawGitBranch = buildData.getBranch("").isEmpty() ? updatedBuildData.getBranch("") : buildData.getBranch("");
        final String gitBranch = normalizeBranch(rawGitBranch);
        if(gitBranch != null) {
            //TODO Remove Java Tracer
            buildSpanOld.setTag(CITags.GIT_BRANCH, gitBranch);
            buildSpan.putMeta(CITags.GIT_BRANCH, gitBranch);
        }

        final String gitTag = normalizeTag(rawGitBranch);
        if(gitTag != null) {
            //TODO Remove Java Tracer
            buildSpanOld.setTag(CITags.GIT_TAG, gitTag);
            buildSpan.putMeta(CITags.GIT_TAG, gitTag);
        }

        final JobNameWrapper jobNameWrapper = new JobNameWrapper(buildData.getJobName(""), gitBranch != null ? gitBranch : gitTag);
        //TODO Remove Java Tracer
        buildSpanOld.setTag(DDTags.RESOURCE_NAME, jobNameWrapper.getTraceJobName());
        buildSpan.setResource(jobNameWrapper.getTraceJobName());

        //TODO Remove Java Tracer
        buildSpanOld.setTag(prefix + CITags._NAME, jobNameWrapper.getTraceJobName());
        buildSpan.putMeta(prefix + CITags._NAME, jobNameWrapper.getTraceJobName());

        if(!jobNameWrapper.getConfigurations().isEmpty()){
            for(Map.Entry<String, String> entry : jobNameWrapper.getConfigurations().entrySet()) {
                //TODO Remove Java Tracer
                buildSpanOld.setTag(prefix + CITags._CONFIGURATION + "." + entry.getKey(), entry.getValue());
                buildSpan.putMeta(prefix + CITags._CONFIGURATION + "." + entry.getKey(), entry.getValue());

            }
        }

        // Stage breakdown
        final StageBreakdownAction stageBreakdownAction = run.getAction(StageBreakdownAction.class);
        if(stageBreakdownAction != null){
            final Map<String, StageData> stageDataByName = stageBreakdownAction.getStageDataByName();
            final List<StageData> stages = new ArrayList<>(stageDataByName.values());
            Collections.sort(stages);

            final String stagesJson = JsonUtils.toJson(new ArrayList<>(stages));
            //TODO Remove Java Tracer
            buildSpanOld.setTag(CITags._DD_CI_STAGES, stagesJson);
            buildSpan.putMeta(CITags._DD_CI_STAGES, stagesJson);
        }

        // Jenkins specific
        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.JENKINS_TAG, buildData.getBuildTag(""));
        buildSpan.putMeta(CITags.JENKINS_TAG, buildData.getBuildTag(""));

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.JENKINS_EXECUTOR_NUMBER, buildData.getExecutorNumber(""));
        buildSpan.putMeta(CITags.JENKINS_EXECUTOR_NUMBER, buildData.getExecutorNumber(""));

        final String jenkinsResult = buildData.getResult("");
        final String pipelineResult = getNormalizedResultForTraces(jenkinsResult);

        //TODO Remove Java Tracer
        buildSpanOld.setTag(prefix + CITags._RESULT, pipelineResult);
        buildSpan.putMeta(prefix + CITags._RESULT, pipelineResult);

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.STATUS, pipelineResult);
        buildSpan.putMeta(CITags.STATUS, pipelineResult);

        //TODO Remove Java Tracer
        buildSpanOld.setTag(CITags.JENKINS_RESULT, jenkinsResult.toLowerCase());
        buildSpan.putMeta(CITags.JENKINS_RESULT, jenkinsResult.toLowerCase());

        if(Result.FAILURE.toString().equals(jenkinsResult)) {
            //TODO Remove Java Tracer
            buildSpanOld.setTag(CITags.ERROR, true);
            buildSpan.setError(true);
        }

        // CI Tags propagation
        final CIGlobalTagsAction ciGlobalTagsAction = run.getAction(CIGlobalTagsAction.class);
        if(ciGlobalTagsAction != null) {
            final Map<String, String> tags = ciGlobalTagsAction.getTags();
            for(Map.Entry<String, String> tagEntry : tags.entrySet()) {
                //TODO Remove Java Tracer
                buildSpanOld.setTag(tagEntry.getKey(), tagEntry.getValue());
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
        //TODO Remove Java Tracer
        buildSpanOld.finish(endTimeMicros - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue));
        buildSpan.setEndNs(TimeUnit.MICROSECONDS.toNanos(endTimeMicros - TimeUnit.MILLISECONDS.toMicros(propagatedMillisInQueue)));
    }

    private String getNodeName(Run<?, ?> run, BuildData buildData, BuildData updatedBuildData) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
        if(pipelineNodeInfoAction != null){
            return pipelineNodeInfoAction.getNodeName();
        }

        return buildData.getNodeName("").isEmpty() ? updatedBuildData.getNodeName("") : buildData.getNodeName("");
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private Set<String> getNodeLabels(Run<?,?> run, final String nodeName) {
        try {
            if(run == null){
                return Collections.emptySet();
            }

            final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
            if(pipelineNodeInfoAction != null) {
                return pipelineNodeInfoAction.getNodeLabels();
            }

            if(run.getExecutor() != null && run.getExecutor().getOwner() != null) {
                Set<String> nodeLabels = DatadogUtilities.getNodeLabels(run.getExecutor().getOwner());
                if(nodeLabels != null && !nodeLabels.isEmpty()) {
                    return nodeLabels;
                }
            }

            // If there is no labels and the node name is master,
            // we force the label "master".
            if("master".equalsIgnoreCase(nodeName)){
                final Set<String> masterLabels = new HashSet<>();
                masterLabels.add("master");
                return masterLabels;
            }

            return Collections.emptySet();
        } catch (Exception ex) {
            logger.fine("Unable to find node labels: " + ex.getMessage());
            return Collections.emptySet();
        }
    }

    private long getMillisInQueue(BuildData buildData) {
        // Reported by the Jenkins Queue API.
        // It's not included in the root span duration.
        final long millisInQueue = buildData.getMillisInQueue(-1L);

        // Reported by a child span.
        // It's included in the root span duration.
        final long propagatedMillisInQueue = buildData.getPropagatedMillisInQueue(-1L);
        return Math.max(Math.max(millisInQueue, propagatedMillisInQueue), 0);
    }

    protected BuildSpanManager getBuildSpanManager() {
        return BuildSpanManager.get();
    }
}
