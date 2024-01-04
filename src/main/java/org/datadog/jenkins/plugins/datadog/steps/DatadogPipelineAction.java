package org.datadog.jenkins.plugins.datadog.steps;

import hudson.model.Action;
import java.io.Serializable;
import java.util.List;
import javax.annotation.CheckForNull;

public class DatadogPipelineAction implements Action, Serializable {
    private static final long serialVersionUID = 1L;

    private boolean collectLogs;
    private List<String> tags;
    private TestVisibility testVisibility;

    public DatadogPipelineAction(boolean collectLogs, List<String> tags, TestVisibility testVisibility) {
        this.collectLogs = collectLogs;
        this.tags = tags;
        this.testVisibility = testVisibility;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean isCollectLogs() {
        return collectLogs;
    }

    public TestVisibility getTestVisibility() {
        return testVisibility;
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
