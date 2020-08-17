package org.datadog.jenkins.plugins.datadog.traces;

import datadog.trace.api.DDTags;
import hudson.model.Run;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildPipeline;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Keeps the logic to send traces related to Jenkins Pipelines.
 */
public class DatadogTracePipelineLogic {

    private static final String CI_PROVIDER = "jenkins";
    private static final Logger logger = Logger.getLogger(DatadogTracePipelineLogic.class.getName());

    private final Tracer tracer;

    public DatadogTracePipelineLogic(Tracer tracer) {
        this.tracer = tracer;
    }

    public void execute(Run run, FlowNode flowNode) {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildTraces()) {
            return;
        }

        if(this.tracer == null) {
            logger.severe("Unable to send pipeline traces. Tracer is null");
            return;
        }

        if(!isLastNode(flowNode)){
            return;
        }

        final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
        if(buildSpanAction == null) {
            return;
        }

        final FlowEndNode flowEndNode = (FlowEndNode) flowNode;
        final BuildPipeline pipeline = new BuildPipeline();
        final DepthFirstScanner scanner = new DepthFirstScanner();
        List<FlowNode> currentHeads = flowEndNode.getExecution().getCurrentHeads();
        scanner.setup(currentHeads);
        scanner.forEach(pipeline::add);

        final SpanContext spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new BuildTextMapAdapter(buildSpanAction.getBuildSpanPropatation()));
        final BuildPipelineNode root = pipeline.buildTree();
        sendTrace(tracer, run, root, spanContext);
    }

    private void sendTrace(final Tracer tracer, final Run run, final BuildPipelineNode current, final SpanContext parentSpanContext) {
        if(!isTraceable(current)){
            logger.severe("Node " + current.getName() + " is not traceable.");
            return;
        }

        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(buildOperationName(current)).withStartTimestamp(current.getStartTimeMicros());

        if(parentSpanContext != null) {
            spanBuilder.asChildOf(parentSpanContext);
        }

        spanBuilder
                .withTag(DDTags.SERVICE_NAME, CI_PROVIDER)
                .withTag(DDTags.RESOURCE_NAME, current.getName())
                .withTag(DDTags.SPAN_TYPE, "ci")
                .withTag(DDTags.LANGUAGE_TAG_KEY, "");

        final Map<String, String> traceTags = buildTraceTags(current);
        for(Map.Entry<String, String> traceTag : traceTags.entrySet()) {
            spanBuilder.withTag(traceTag.getKey(), traceTag.getValue());
        }

        final Span span = spanBuilder.start();

        for(final BuildPipelineNode child : current.getChildren()) {
            sendTrace(tracer, run, child, span.context());
        }

        //Logs
        //NOTE: Implement sendNodeLogs

        span.finish(current.getEndTimeMicros());
    }


    private Map<String, String> buildTraceTags(BuildPipelineNode current) {
        final String normalizedPrefix = current.getType().getNormalizedName();
        final String prefix = current.getType().getName();
        final Map<String, String> envVars = current.getEnvVars();

        final Map<String, String> tags = new HashMap<>();
        tags.put(CITags.CI_PROVIDER, CI_PROVIDER);
        tags.put(normalizedPrefix + ".id", current.getId());
        tags.put(normalizedPrefix + ".name", current.getName());
        tags.put(CI_PROVIDER + ".internal", String.valueOf(current.isInternal()));
        tags.put(CI_PROVIDER + ".result", current.getResult());
        tags.put(normalizedPrefix + ".workspace", current.getWorkspace());
        tags.put(normalizedPrefix + ".url", envVars.get("BUILD_URL"));
        tags.put("error", String.valueOf(current.isError()));

        tags.put(CITags.REPOSITORY_BRANCH, envVars.get("GIT_BRANCH"));
        tags.put(CITags.REPOSITORY_COMMIT, envVars.get("GIT_COMMIT"));
        tags.put(CITags.REPOSITORY_URL, envVars.get("GIT_URL"));

        tags.put(CITags.USER_NAME, envVars.get("USER"));

        tags.put(CITags.NODE_NAME, current.getNodeName());
        tags.put(CITags.NODE_HOSTNAME, current.getNodeHostname());

        // Arguments
        for(Map.Entry<String, Object> entry : current.getArgs().entrySet()) {
            tags.put(CI_PROVIDER + "." + prefix + ".args."+entry.getKey(), String.valueOf(entry.getValue()));

            if("script".equals(entry.getKey())){
                tags.put(normalizedPrefix + ".script", String.valueOf(entry.getValue()));
            }
        }

        // Errors
        if(current.getErrorObj() != null){
            final Throwable error = current.getErrorObj();
            tags.put(DDTags.ERROR_MSG, error.getMessage());
            tags.put(DDTags.ERROR_TYPE, error.getClass().getName());

            final StringWriter errorString = new StringWriter();
            error.printStackTrace(new PrintWriter(errorString));
            tags.put(DDTags.ERROR_STACK, errorString.toString());
        }

        return tags;
    }


    private String buildOperationName(BuildPipelineNode current) {
        return CI_PROVIDER + "." + current.getType().name().toLowerCase() + ((current.isInternal()) ? ".internal" : "");
    }

    private boolean isTraceable(BuildPipelineNode node) {
        if (node.getStartTimeMicros() == -1L) {
            logger.severe("Unable to send trace of node: " + node.getName() + ". Start Time is not set");
            return false;
        }

        if(node.getEndTimeMicros() == -1L) {
            logger.severe("Unable to send trace of node: " + node.getName() + ". End Time is not set");
            return false;
        }

        return true;
    }

    /**
     * Check if flowNode is the last node of the pipeline.
     * @param flowNode
     * @return true if flowNode is the last node of the pipeline
     */
    private boolean isLastNode(FlowNode flowNode) {
        return flowNode instanceof FlowEndNode;
    }
}
