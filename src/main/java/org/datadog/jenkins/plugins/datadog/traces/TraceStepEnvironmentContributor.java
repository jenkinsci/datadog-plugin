package org.datadog.jenkins.plugins.datadog.traces;

import datadog.trace.api.DDId;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepEnvironmentContributor;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Contributes the X_DATADOG_TRACE_ID and X_DATADOG_PARENT_ID environment variables to workflow steps.
 * The value of the X_DATADOG_PARENT_ID is the spanID of the span that is going to be executed, therefore
 * this method is executed as many as workflow steps the pipeline have.
 */
@Extension
public class TraceStepEnvironmentContributor extends StepEnvironmentContributor {

    private static final Logger logger = Logger.getLogger(TraceStepEnvironmentContributor.class.getName());

    public static final String TRACE_ID_ENVVAR_KEY = "X_DATADOG_TRACE_ID";
    public static final String SPAN_ID_ENVVAR_KEY = "X_DATADOG_PARENT_ID";

    @Override
    public void buildEnvironmentFor(StepContext stepContext, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
        try {
            final Run<?,?> run = stepContext.get(Run.class);
            final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
            if(buildSpanAction != null) {
                // The key 'x-datadog-trace-id' is used by the Java Tracer to inject the SpanContext
                // into the buildSpanPropagation map. As we don't have access to the tracer here,
                // we use the key directly to obtain the traceID value.
                final String traceId = buildSpanAction.getBuildSpanPropatation().get("x-datadog-trace-id");
                envs.put(TRACE_ID_ENVVAR_KEY, DDId.from(traceId).toString());
            }

            final FlowNode flowNode = stepContext.get(FlowNode.class);
            if(flowNode != null) {
                final GeneratedSpanIdAction idsAction = flowNode.getAction(GeneratedSpanIdAction.class);
                if(idsAction != null) {
                    envs.put(SPAN_ID_ENVVAR_KEY, idsAction.getDDSpanId().toString());
                }
            }
        } catch (Exception ex) {
            logger.severe("Unable to set traces IDs as environment variables before step execution. " + ex);
        }

    }
}
