package org.datadog.jenkins.plugins.datadog.publishers;

import org.datadog.jenkins.plugins.datadog.DatadogClient;

public class DatadogComputerPublisherTestWrapper extends DatadogComputerPublisher {
    DatadogClient client;

    public void setDatadogClient(DatadogClient client) {
        this.client = client;
    }

    public DatadogClient getDatadogClient(){
        return this.client;
    }
}