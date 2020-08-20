package org.datadog.jenkins.plugins.datadog.traces;

import org.datadog.jenkins.plugins.datadog.model.StepData;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to associated StepDescriptor with a StepData object.
 * This mechanism is used to propagate information from the {@code Step} execution to the {@code StepNode}
 */
public class StepDataManager {

    private static final StepDataManager INSTANCE = new StepDataManager();
    private final Map<StepDescriptor, StepData> stepDataByDescriptor = new HashMap<>();

    public static StepDataManager get() {
        return INSTANCE;
    }

    public StepData put(final StepDescriptor descriptor, final StepData stepData) {
        return stepDataByDescriptor.put(descriptor, stepData);
    }

    public StepData get(final StepDescriptor descriptor) {
        return stepDataByDescriptor.get(descriptor);
    }

    public StepData remove(final StepDescriptor descriptor){
        return stepDataByDescriptor.remove(descriptor);
    }
}
