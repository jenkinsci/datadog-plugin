package org.datadog.jenkins.plugins.datadog.traces;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepEnvironmentContributor;

import java.io.IOException;

@Extension
public class TraceStepEnvironmentContributor extends StepEnvironmentContributor {

    public static final String TRACE_ID_ENVVAR_KEY = "X-DATADOG-TRACE-ID";
    public static final String SPAN_ID_ENVVAR_KEY = "X-DATADOG-PARENT-ID";

    @Override
    public void buildEnvironmentFor(StepContext stepContext, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
        final Run<?,?> run = stepContext.get(Run.class);
        final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
        if(buildSpanAction != null) {
            // The key 'x-datadog-trace-id' is used by the Java Tracer to inject the SpanContext
            // into the buildSpanPropagation map. As we don't have access to the tracer here,
            // we use the key directly to obtain the traceID value.
            final String traceId = buildSpanAction.getBuildSpanPropatation().get("x-datadog-trace-id");
            envs.put(TRACE_ID_ENVVAR_KEY, String.valueOf(traceId));
        }

        final FlowNode flowNode = stepContext.get(FlowNode.class);
        if(flowNode != null) {
            final GeneratedSpanIdAction idsAction = flowNode.getAction(GeneratedSpanIdAction.class);
            if(idsAction != null) {
                envs.put(SPAN_ID_ENVVAR_KEY, idsAction.getDDSpanId().toString());
            }
        }
    }
}
