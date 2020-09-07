package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getNormalizedResultForTraces;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeBranch;
import static org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils.normalizeTag;

import datadog.trace.api.DDTags;
import hudson.model.Result;
import hudson.model.Run;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.StepData;

import java.util.logging.Logger;

/**
 * Keeps the logic to send traces related to Jenkins Build.
 */
public class DatadogTraceBuildLogic {

    private static final Logger logger = Logger.getLogger(DatadogTraceBuildLogic.class.getName());

    private final Tracer tracer;

    public DatadogTraceBuildLogic(final Tracer tracer) {
        this.tracer = tracer;
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
        final long endTimeMicros = buildData.getEndTime(0L) * 1000;
        buildSpan.setTag(DDTags.SERVICE_NAME, DatadogUtilities.getDatadogGlobalDescriptor().getTraceServiceName());
        buildSpan.setTag(DDTags.RESOURCE_NAME, buildData.getJobName(null));
        buildSpan.setTag(DDTags.SPAN_TYPE, "ci");
        buildSpan.setTag(CITags.CI_PROVIDER_NAME, "jenkins");
        buildSpan.setTag(DDTags.LANGUAGE_TAG_KEY, "");
        buildSpan.setTag(CITags._DD_CI_INTERNAL, false);
        buildSpan.setTag(CITags.USER_NAME, buildData.getUserId());
        buildSpan.setTag(prefix + CITags._ID, buildData.getBuildTag(""));
        buildSpan.setTag(prefix + CITags._NAME, buildData.getJobName(""));
        buildSpan.setTag(prefix + CITags._NUMBER, buildData.getBuildNumber(""));
        buildSpan.setTag(prefix + CITags._URL, buildData.getBuildUrl(""));

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
        buildSpan.setTag(CITags.GIT_COMMIT_SHA, gitCommit);

        final String rawGitBranch = buildData.getBranch("").isEmpty() ? pipelineData.getBranch("") : buildData.getBranch("");
        final String gitBranch = normalizeBranch(rawGitBranch);
        if(gitBranch != null) {
            buildSpan.setTag(CITags.GIT_BRANCH, gitBranch);
        }

        final String gitTag = normalizeTag(rawGitBranch);
        if(gitTag != null) {
            buildSpan.setTag(CITags.GIT_TAG, gitTag);
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
