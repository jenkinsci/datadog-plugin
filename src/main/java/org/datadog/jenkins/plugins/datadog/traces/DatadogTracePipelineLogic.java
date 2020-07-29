package org.datadog.jenkins.plugins.datadog.traces;

import datadog.trace.api.DDTags;
import hudson.console.AnnotatedLargeText;
import hudson.model.Run;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.logs.DatadogOutputStream;
import org.datadog.jenkins.plugins.datadog.logs.DatadogWriter;
import org.datadog.jenkins.plugins.datadog.model.BuildPipeline;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class DatadogTracePipelineLogic {

    private static final String CI_PROVIDER = "jenkins";

    private static final DatadogTracePipelineLogic INSTANCE = new DatadogTracePipelineLogic();
    private static final Logger logger = Logger.getLogger(DatadogTracePipelineLogic.class.getName());

    private final Tracer tracer;

    public DatadogTracePipelineLogic() {
        this.tracer = ClientFactory.getClient().tracer();
    }

    public void onNewHead(Run run, FlowNode flowNode) {
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
        scanner.setup(flowEndNode.getExecution().getCurrentHeads());
        scanner.forEach(pipeline::add);

        final SpanContext spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new BuildTextMapAdapter(buildSpanAction.getBuildSpanPropatation()));
        final BuildPipelineNode root = pipeline.buildTree();
        sendTrace(run, root, spanContext);
    }

    private void sendTrace(final Run run, final BuildPipelineNode current, final SpanContext parentSpanContext) {
        if(!isTraceable(current)){
            logger.severe("Node " + current.getName() + " is not traceable.");
            return;
        }

        final Tracer.SpanBuilder spanBuilder = this.tracer.buildSpan(buildOperationName(current)).withStartTimestamp(current.getStartTimeMicros());

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
            sendTrace(run, child, span.context());
        }

        //Logs
        sendLogs(run, current, span.context());

        span.finish(current.getEndTimeMicros());
    }

    private void sendLogs(final Run run, BuildPipelineNode current, final SpanContext spanContext) {
        try {
            final AnnotatedLargeText logText = current.getLogText();
            if(logText != null) {
                final DatadogWriter writer = new DatadogWriter(run, run.getCharset(), spanContext);
                final OutputStream out = new DatadogOutputStream(writer);
                logText.writeLogTo(0, out);
            }
        } catch (Exception e){
            logger.severe("Unable to send logs of node " + current.getName());
        }

    }

    private Map<String, String> buildTraceTags(BuildPipelineNode current) {
        final String prefix = current.getType().name().toLowerCase();
        final Map<String, String> envVars = current.getEnvVars();

        final Map<String, String> tags = new HashMap<>();
        tags.put("ci.provider", CI_PROVIDER);
        tags.put(prefix + ".id", current.getId());
        tags.put(prefix + ".name", current.getName());
        tags.put(CI_PROVIDER + ".internal", String.valueOf(current.isInternal()));
        tags.put(CI_PROVIDER + ".result", current.getResult());
        tags.put("job.workspace", current.getWorkspace());
        tags.put("job.url", envVars.get("BUILD_URL"));
        tags.put("error", String.valueOf(current.isError()));

        tags.put("repository.branch", envVars.get("GIT_BRANCH"));
        tags.put("repository.commit", envVars.get("GIT_COMMIT"));
        tags.put("repository.url", envVars.get("GIT_URL"));

        tags.put("user.principal", envVars.get("USER"));

        tags.put("node.name", current.getNodeName());
        tags.put("node.hostname", current.getNodeHostname());

        // Arguments
        for(Map.Entry<String, Object> entry : current.getArgs().entrySet()) {
            tags.put(CI_PROVIDER + "." + prefix + ".args."+entry.getKey(), String.valueOf(entry.getValue()));

            if("script".equals(entry.getKey())){
                tags.put("job.script", String.valueOf(entry.getValue()));
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

        //Logs
        //TBD

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

    public static DatadogTracePipelineLogic get() {
        return INSTANCE;
    }

}
