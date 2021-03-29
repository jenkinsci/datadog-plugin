package org.datadog.jenkins.plugins.datadog.listeners;

import hudson.Extension;
import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.model.StepData;
import org.datadog.jenkins.plugins.datadog.traces.StepDataAction;
import org.jenkinsci.plugins.workflow.flow.StepListener;
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

            stepDataAction.put(step.getDescriptor(), new StepData(context));
        } catch (Exception ex) {
            logger.severe("Unable to extract Run information of the StepContext. " + ex);
        }
    }
}
