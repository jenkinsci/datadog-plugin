package org.datadog.jenkins.plugins.datadog.listeners;

import datadog.trace.api.IdGenerationStrategy;
import hudson.Extension;
import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.StepData;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.GeneratedSpanIdAction;
import org.datadog.jenkins.plugins.datadog.traces.StepDataAction;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

@Extension
public class DatadogStepListener implements StepListener {

    private static final Logger logger = Logger.getLogger(DatadogStepListener.class.getName());

    @Override
    public void notifyOfNewStep(@Nonnull Step step, @Nonnull StepContext context) {
        try {
            final Run<?,?> run = context.get(Run.class);
            final StepDataAction stepDataAction = run.getAction(StepDataAction.class);
            if(stepDataAction == null) {
                logger.fine("Unable to store Step data in Run '"+run.getFullDisplayName()+"'. StepDataAction is null");
                return;
            }

            // We cannot store the generated spanID in the StepData because
            // the same StepDescriptor is used for multiple related FlowNodes.
            stepDataAction.put(step.getDescriptor(), new StepData(context));

            final FlowNode flowNode = context.get(FlowNode.class);
            if(flowNode == null) {
                logger.fine("Unable to generate trace ids. FlowNode is null");
                return;
            }

            final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
            if(buildSpanAction == null) {
                logger.fine("Unable to generate trace ids. BuildSpanAction is null in Run: "+run.getFullDisplayName());
                return;
            }

            final DatadogGlobalConfiguration datadogGlobalDescriptor = DatadogUtilities.getDatadogGlobalDescriptor();
            if(datadogGlobalDescriptor == null) {
                logger.fine("Unable to generate trace ids. DatadogGlobalConfiguration is null");
                return;
            }

            final IdGenerationStrategy traceIdsGenerator = datadogGlobalDescriptor.getTraceIdsGenerator();

            // We store the action directly in the concrete FlowNode. It's not possible
            // to use the StepDataAction, because the same StepDescriptor is used
            // for multiple related FlowNodes.
            final GeneratedSpanIdAction idsAction = new GeneratedSpanIdAction(traceIdsGenerator.generate());
            flowNode.addAction(idsAction);
        } catch (Exception ex) {
            logger.severe("Unable to extract Run information of the StepContext. " + ex);
        }
    }
}
