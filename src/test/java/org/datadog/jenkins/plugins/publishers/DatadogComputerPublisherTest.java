package org.datadog.jenkins.plugins.publishers;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.publishers.DatadogComputerPublisher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;


public class DatadogComputerPublisherTest {
    @Rule 
    public JenkinsRule jenkins = new JenkinsRule();
    
    @Test
    public void testJenkinsNodeMetrics() throws Exception {
        DatadogClientStub client = new DatadogClientStub();
        DatadogComputerPublisher computerPublisher = new DatadogComputerPublisherTestWrapper();
        ((DatadogComputerPublisherTestWrapper)computerPublisher).setDatadogClient(client);
        
        String url = jenkins.getURL().toString();
        String hostname = DatadogUtilities.getHostname(null);
        String[] expectedTags = new String[3];
        expectedTags[0] = "node_name:master";
        expectedTags[1] = "node_label:master";
        expectedTags[2] = "jenkins_url:" + url;
        jenkins.jenkins.createComputer();
        computerPublisher.doRun();
        
        client.assertMetric("jenkins.node_status.count", 1, hostname, expectedTags);
    }
}