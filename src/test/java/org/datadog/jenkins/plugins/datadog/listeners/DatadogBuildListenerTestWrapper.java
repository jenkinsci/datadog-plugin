package org.datadog.jenkins.plugins.datadog.listeners;

import hudson.model.Queue;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTraceBuildLogic;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

public class DatadogBuildListenerTestWrapper extends DatadogBuildListener {
    Queue queue;
    DatadogClient client;
    DatadogTraceBuildLogic traceBuildLogic;
    long pauseDurationMillis;

    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    public void setDatadogClient(DatadogClient client) {
        this.client = client;
    }

    public Queue getQueue(){
        return this.queue;
    }

    public DatadogClient getDatadogClient(){
        return this.client;
    }

    public DatadogTraceBuildLogic getTraceBuildLogic(){
        return this.traceBuildLogic;
    }

    public void setStubbedPauseDurationMillis(long pauseDurationMillis){
        this.pauseDurationMillis = pauseDurationMillis;
    }

    @Override
    public long getPauseDurationMillis(WorkflowRun r){
        return this.pauseDurationMillis;
    }
}
