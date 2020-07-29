package org.datadog.jenkins.plugins.datadog.steps;

import hudson.model.Action;
import java.io.Serializable;
import java.util.List;
import javax.annotation.CheckForNull;

public class DatadogPipelineAction implements Action, Serializable {
    private static final long serialVersionUID = 1L;

    private boolean collectLogs;
    private List<String> tags;

    public DatadogPipelineAction(boolean collectLogs, List<String> tags) {
        this.collectLogs = collectLogs;
        this.tags = tags;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean isCollectLogs() {
        return collectLogs;
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
