package org.datadog.jenkins.plugins.datadog.traces;

import datadog.trace.api.DDTags;
import hudson.model.Result;
import hudson.model.Run;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.model.BuildData;

import java.util.logging.Logger;

public class DatadogTraceBuildLogic {

    private static final DatadogTraceBuildLogic INSTANCE = new DatadogTraceBuildLogic();

    private static final Logger logger = Logger.getLogger(DatadogTraceBuildLogic.class.getName());

    public void onStarted(final BuildData buildData, Run run) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildTraces()) {
            logger.fine("Trace Collection disabled");
            return;
        }

        // Get Datadog Client Instance
        DatadogClient client = getDatadogClient();
        if (client == null) {
            return;
        }

        // Traces
        final Tracer tracer = client.tracer();
        final long startTimeMicros = buildData.getStartTime(0L) * 1000;

        final Span buildSpan = tracer.buildSpan("jenkins.build")
                .withStartTimestamp(startTimeMicros)
                .start();

        getBuildSpanManager().put(buildData.getBuildTag(""), buildSpan);
        final BuildSpanAction buildSpanAction = BuildSpanAction.newAction();
        tracer.inject(buildSpan.context(), Format.Builtin.TEXT_MAP, new BuildTextMapAdapter(buildSpanAction.getBuildSpanPropatation()));
        run.addAction(buildSpanAction);
    }

    public void onCompleted(final BuildData buildData) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildTraces()) {
            return;
        }

        // APM Traces
        final Span buildSpan = getBuildSpanManager().remove(buildData.getBuildTag(""));
        if(buildSpan == null) {
            return;
        }

        final long endTimeMicros = buildData.getEndTime(0L) * 1000;
        buildSpan.setTag(DDTags.SERVICE_NAME, "jenkins");
        buildSpan.setTag(DDTags.RESOURCE_NAME, buildData.getJobName(null));
        buildSpan.setTag(DDTags.SPAN_TYPE, "ci");
        buildSpan.setTag("ci.provider", "jenkins");
        buildSpan.setTag(DDTags.LANGUAGE_TAG_KEY, "");
        buildSpan.setTag(DDTags.USER_NAME, buildData.getUserId());
        buildSpan.setTag("pipeline.id", buildData.getBuildId(""));
        buildSpan.setTag("pipeline.name", buildData.getJobName(""));
        buildSpan.setTag("pipeline.number", buildData.getBuildNumber(""));
        buildSpan.setTag("pipeline.workspace", buildData.getWorkspace(""));
        buildSpan.setTag("node.name", buildData.getNodeName(""));
        buildSpan.setTag("repository.url", buildData.getGitUrl(""));
        buildSpan.setTag("repository.branch", buildData.getBranch(""));
        buildSpan.setTag("repository.commit", buildData.getGitCommit(""));
        buildSpan.setTag("jenkins.tag", buildData.getBuildTag(""));
        buildSpan.setTag("jenkins.executor.number", buildData.getExecutorNumber(""));

        final String result = buildData.getResult("");
        buildSpan.setTag("jenkins.result", result);
        if(Result.FAILURE.toString().equals(result)) {
            buildSpan.setTag("error", true);
        }

        buildSpan.finish(endTimeMicros);
    }

    public static DatadogTraceBuildLogic get() {
        return INSTANCE;
    }

    protected DatadogClient getDatadogClient() {
        return ClientFactory.getClient();
    }

    protected BuildSpanManager getBuildSpanManager() {
        return BuildSpanManager.get();
    }
}
