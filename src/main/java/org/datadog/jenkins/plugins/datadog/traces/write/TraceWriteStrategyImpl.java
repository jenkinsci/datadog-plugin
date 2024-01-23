package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.traces.DatadogBaseBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogBasePipelineLogic;
import org.datadog.jenkins.plugins.datadog.util.CircuitBreaker;

public class TraceWriteStrategyImpl implements TraceWriteStrategy {

    private static final Logger logger = Logger.getLogger(TraceWriteStrategyImpl.class.getName());

    private final DatadogBaseBuildLogic buildLogic;
    private final DatadogBasePipelineLogic pipelineLogic;
    private final CircuitBreaker<List<JSONObject>> sendSpansCircuitBreaker;

    public TraceWriteStrategyImpl(DatadogBaseBuildLogic buildLogic, DatadogBasePipelineLogic pipelineLogic, Consumer<List<JSONObject>> spansSender) {
        this.buildLogic = buildLogic;
        this.pipelineLogic = pipelineLogic;
        this.sendSpansCircuitBreaker = new CircuitBreaker<>(
                spansSender,
                this::logTransportBroken,
                this::logTransportError
        );
    }

    @Nullable
    @Override
    public JSONObject serialize(final BuildData buildData, final Run<?, ?> run) {
        return buildLogic.toJson(buildData, run);
    }

    @Nonnull
    @Override
    public JSONObject serialize(BuildPipelineNode node, Run<?, ?> run) throws IOException, InterruptedException {
        return pipelineLogic.toJson(node, run);
    }

    @Override
    public void send(List<JSONObject> spans) {
        sendSpansCircuitBreaker.accept(spans);
    }

    private void logTransportBroken(List<net.sf.json.JSONObject> spans) {
        logger.fine("Ignoring " + spans.size() + " because transport is broken");
    }

    private void logTransportError(Exception e) {
        DatadogUtilities.severe(logger, e, "Error while sending trace");
    }
}
