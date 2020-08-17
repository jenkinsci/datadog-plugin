package org.datadog.jenkins.plugins.datadog.traces;

import datadog.trace.api.DDTags;
import hudson.model.Result;
import hudson.model.Run;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;

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
        final BuildSpanAction buildSpanAction = new BuildSpanAction();
        this.tracer.inject(buildSpan.context(), Format.Builtin.TEXT_MAP, new BuildTextMapAdapter(buildSpanAction.getBuildSpanPropatation()));
        run.addAction(buildSpanAction);
    }

    public void finishBuildTrace(final BuildData buildData) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildTraces()) {
            return;
        }

        // APM Traces
        final Span buildSpan = getBuildSpanManager().remove(buildData.getBuildTag(""));
        if(buildSpan == null) {
            return;
        }

        final String prefix = BuildPipelineNode.NodeType.PIPELINE.getNormalizedName();
        final long endTimeMicros = buildData.getEndTime(0L) * 1000;
        buildSpan.setTag(DDTags.SERVICE_NAME, "jenkins");
        buildSpan.setTag(DDTags.RESOURCE_NAME, buildData.getJobName(null));
        buildSpan.setTag(DDTags.SPAN_TYPE, "ci");
        buildSpan.setTag(CITags.CI_PROVIDER, "jenkins");
        buildSpan.setTag(DDTags.LANGUAGE_TAG_KEY, "");
        buildSpan.setTag(CITags.USER_NAME, buildData.getUserId());
        buildSpan.setTag(prefix + CITags._ID, buildData.getBuildId(""));
        buildSpan.setTag(prefix + CITags._NAME, buildData.getJobName(""));
        buildSpan.setTag(prefix + CITags._NUMBER, buildData.getBuildNumber(""));
        buildSpan.setTag(prefix + CITags._WORKSPACE, buildData.getWorkspace(""));
        buildSpan.setTag(CITags.NODE_NAME, buildData.getNodeName(""));
        buildSpan.setTag(CITags.REPOSITORY_URL, buildData.getGitUrl(""));
        buildSpan.setTag(CITags.REPOSITORY_BRANCH, buildData.getBranch(""));
        buildSpan.setTag(CITags.REPOSITORY_COMMIT, buildData.getGitCommit(""));
        buildSpan.setTag(CITags.JENKINS_TAG, buildData.getBuildTag(""));
        buildSpan.setTag(CITags.JENKINS_EXECUTOR_NUMBER, buildData.getExecutorNumber(""));

        final String jenkinsResult = buildData.getResult("");
        final String pipelineResult = DatadogUtilities.getNormalizedResult(Result.fromString(jenkinsResult));
        buildSpan.setTag(prefix + CITags._RESULT, pipelineResult);
        buildSpan.setTag(CITags.JENKINS_RESULT, jenkinsResult);
        if(Result.FAILURE.toString().equals(jenkinsResult)) {
            buildSpan.setTag(CITags.ERROR, true);
        }

        buildSpan.finish(endTimeMicros);
    }

    protected BuildSpanManager getBuildSpanManager() {
        return BuildSpanManager.get();
    }
}
