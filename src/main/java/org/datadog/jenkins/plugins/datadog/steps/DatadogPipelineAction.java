package org.datadog.jenkins.plugins.datadog.steps;

import hudson.model.Action;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import javax.annotation.CheckForNull;

public class DatadogPipelineAction implements Action, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean collectLogs;
    private final List<String> tags;
    private final TestOptimization testOptimization;

    public DatadogPipelineAction(boolean collectLogs, List<String> tags, TestOptimization testOptimization) {
        this.collectLogs = collectLogs;
        this.tags = tags;
        this.testOptimization = testOptimization;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean isCollectLogs() {
        return collectLogs;
    }

    public TestOptimization getTestOptimization() {
        return testOptimization;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "Datadog";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "datadog";
    }
}
