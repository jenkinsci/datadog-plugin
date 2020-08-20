package org.datadog.jenkins.plugins.datadog.listeners;

import hudson.Extension;
import org.datadog.jenkins.plugins.datadog.model.StepData;
import org.datadog.jenkins.plugins.datadog.traces.StepDataManager;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import javax.annotation.Nonnull;

@Extension
public class DatadogStepListener implements StepListener {

    @Override
    public void notifyOfNewStep(@Nonnull Step step, @Nonnull StepContext context) {
        StepDataManager.get().put(step.getDescriptor(), new StepData(context));
    }

}
