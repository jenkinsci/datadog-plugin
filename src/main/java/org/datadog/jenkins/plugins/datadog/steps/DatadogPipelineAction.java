package org.datadog.jenkins.plugins.datadog.steps;

import hudson.model.Action;
import java.io.Serializable;
import javax.annotation.CheckForNull;

public class DatadogPipelineAction implements Action, Serializable {
    private static final long serialVersionUID = 1L;

    private boolean collectLogs;

    public DatadogPipelineAction(boolean collectLogs) {
        this.collectLogs = collectLogs;
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
