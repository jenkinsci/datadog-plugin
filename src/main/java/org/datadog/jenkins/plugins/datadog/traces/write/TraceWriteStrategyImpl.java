package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.traces.DatadogBaseBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogBasePipelineLogic;
import org.datadog.jenkins.plugins.datadog.util.CircuitBreaker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

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

    @Override
    public JSONObject serialize(final BuildData buildData, final Run<?, ?> run) {
        return buildLogic.finishBuildTrace(buildData, run);
    }

    @Override
    public Collection<JSONObject> serialize(FlowNode flowNode, Run<?, ?> run) {
        return pipelineLogic.execute(flowNode, run);
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
