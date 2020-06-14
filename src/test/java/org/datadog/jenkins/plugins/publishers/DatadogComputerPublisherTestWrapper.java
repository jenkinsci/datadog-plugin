package org.datadog.jenkins.plugins.publishers;

import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.publishers.DatadogComputerPublisher;

public class DatadogComputerPublisherTestWrapper extends DatadogComputerPublisher {
    DatadogClient client;

    public void setDatadogClient(DatadogClient client) {
        this.client = client;
    }

    public DatadogClient getDatadogClient(){
        return this.client;
    }
}