package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.traces.DatadogBaseBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogBasePipelineLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTraceBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTracePipelineLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookPipelineLogic;
import org.datadog.jenkins.plugins.datadog.util.CircuitBreaker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

public class TraceWriteStrategyImpl implements TraceWriteStrategy {

    private static final Logger logger = Logger.getLogger(TraceWriteStrategyImpl.class.getName());

    private final Track track;
    private final DatadogBaseBuildLogic buildLogic;
    private final DatadogBasePipelineLogic pipelineLogic;
    private final CircuitBreaker<Collection<Payload>> sendSpansCircuitBreaker;

    public TraceWriteStrategyImpl(Track track, Consumer<Collection<Payload>> spansSender) {
        if (track == Track.APM) {
            this.buildLogic = new DatadogTraceBuildLogic();
            this.pipelineLogic = new DatadogTracePipelineLogic();
        } else if (track == Track.WEBHOOK) {
            this.buildLogic = new DatadogWebhookBuildLogic();
            this.pipelineLogic = new DatadogWebhookPipelineLogic();
        } else {
            throw new IllegalArgumentException("Unexpected track value: " + track);
        }
        this.track = track;
        this.sendSpansCircuitBreaker = new CircuitBreaker<>(
                spansSender,
                this::logTransportBroken,
                this::logTransportError
        );
    }

    @Override
    public Payload serialize(final BuildData buildData, final Run<?, ?> run) {
        JSONObject buildSpan = buildLogic.finishBuildTrace(buildData, run);
        return buildSpan != null ? new Payload(buildSpan, track) : null;
    }

    @Nonnull
    @Override
    public Collection<Payload> serialize(FlowNode flowNode, Run<?, ?> run) {
        Collection<JSONObject> stepSpans = pipelineLogic.execute(flowNode, run);
        return stepSpans.stream().map(payload -> new Payload(payload, track)).collect(Collectors.toList());
    }

    @Override
    public void send(Collection<Payload> serializationResult) {
        sendSpansCircuitBreaker.accept(serializationResult);
    }

    private void logTransportBroken(Collection<Payload> spans) {
        logger.fine("Ignoring " + spans.size() + " because transport is broken");
    }

    private void logTransportError(Exception e) {
        DatadogUtilities.severe(logger, e, "Error while sending trace");
    }
}
