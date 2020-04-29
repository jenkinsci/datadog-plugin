package org.datadog.jenkins.plugins.datadog.listeners;

import com.cloudbees.workflow.rest.external.RunExt;
import hudson.model.Queue;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

public class DatadogBuildListenerTestWrapper extends DatadogBuildListener {
    Queue queue;
    DatadogClient client;
    RunExt runExt;

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

    public void setStubbedRunExt(RunExt r){
        this.runExt = r;
    }

    @Override
    public RunExt getRunExtForRun(WorkflowRun r){
        return this.runExt;
    }
}
