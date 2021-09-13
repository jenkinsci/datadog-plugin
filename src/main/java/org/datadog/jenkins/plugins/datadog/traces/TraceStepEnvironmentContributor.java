package org.datadog.jenkins.plugins.datadog.traces;

import static org.datadog.jenkins.plugins.datadog.traces.TracerConstants.SPAN_ID_ENVVAR_KEY;
import static org.datadog.jenkins.plugins.datadog.traces.TracerConstants.TRACE_ID_ENVVAR_KEY;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.StepTraceData;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepEnvironmentContributor;

import java.io.IOException;
import java.util.logging.Logger;

@Extension
public class TraceStepEnvironmentContributor extends StepEnvironmentContributor {

    private static final Logger logger = Logger.getLogger(TraceStepEnvironmentContributor.class.getName());

    @Override
    public void buildEnvironmentFor(StepContext stepContext, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
        if (!DatadogUtilities.getDatadogGlobalDescriptor().isEnabledCiVisibility()) {
            return;
        }

        try {
            final Run<?,?> run = stepContext.get(Run.class);
            if(run == null) {
                logger.fine("Unable to set trace ids as environment variables. Run is null");
                return;
            }

            final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
            if(buildSpanAction == null) {
                logger.fine("Unable to set trace ids as environment variables. in Run '"+run.getFullDisplayName()+"'. BuildSpanAction is null");
                return;
            }

            final StepTraceDataAction stepTraceDataAction = run.getAction(StepTraceDataAction.class);
            if(stepTraceDataAction == null) {
                logger.fine("Unable to set trace ids as environment variables. in Run '"+run.getFullDisplayName()+"'. StepTraceDataAction is null");
                return;
            }

            final FlowNode flowNode = stepContext.get(FlowNode.class);
            if(flowNode == null) {
                logger.fine("Unable to set trace ids as environment variables. in Run '"+run.getFullDisplayName()+"'. FlowNode is null");
                return;
            }

            if(!(flowNode instanceof StepAtomNode)){
                return;
            }

            StepTraceData stepTraceData = stepTraceDataAction.synchronizedGet(run, flowNode);
            if(stepTraceData == null){
                stepTraceData = new StepTraceData(IdGenerator.generate());
                stepTraceDataAction.synchronizedPut(run, flowNode, stepTraceData);
            }

            final String traceIdStr = Long.toUnsignedString(buildSpanAction.getBuildSpanContext().getTraceId());
            final String spanIdStr  = Long.toUnsignedString(stepTraceData.getSpanId());
            envs.put(TRACE_ID_ENVVAR_KEY, traceIdStr);
            envs.put(SPAN_ID_ENVVAR_KEY, spanIdStr);
            logger.fine("Set DD_CUSTOM_TRACE_ID="+traceIdStr+", DD_CUSTOM_PARENT_ID="+spanIdStr+" for FlowNode: "+flowNode);

        } catch (Exception ex) {
            logger.severe("Unable to set traces IDs as environment variables before step execution. " + ex);
        }
    }

}
