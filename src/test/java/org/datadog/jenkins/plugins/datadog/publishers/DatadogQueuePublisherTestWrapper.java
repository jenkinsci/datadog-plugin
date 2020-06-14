package org.datadog.jenkins.plugins.datadog.publishers;

import hudson.model.Queue;
import org.datadog.jenkins.plugins.datadog.DatadogClient;

public class DatadogQueuePublisherTestWrapper extends DatadogQueuePublisher {
    Queue queue;
    DatadogClient client;
    
    public void setQueue(Queue newQueue) {
        this.queue = newQueue;
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
}
