package org.datadog.jenkins.plugins.datadog.publishers;

import java.util.Arrays;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.EnvVars;
import hudson.model.Messages;
import hudson.model.Computer;
import hudson.slaves.OfflineCause;

public class DatadogComputerPublisherTest {
    @Rule 
    public JenkinsRule jenkins = new JenkinsRule();
    
    @Test
    public void testJenkinsNodeMetrics() throws Exception {
        DatadogClientStub client = new DatadogClientStub();
        DatadogComputerPublisherTestWrapper computerPublisher = new DatadogComputerPublisherTestWrapper();
        ((DatadogComputerPublisherTestWrapper)computerPublisher).setDatadogClient(client);
        
        String url = jenkins.getURL().toString();
        String hostname = DatadogUtilities.getHostname(null);
        String nodeHostname = null;
        for (Computer computer: jenkins.jenkins.getComputers()){
            nodeHostname = computer.getHostName();
        }
        
        String[] expectedTags = new String[3];
        expectedTags[0] = "node_name:master";
        expectedTags[1] = "node_label:master";
        expectedTags[2] = "jenkins_url:" + url;
        
        // The CI sets a hostname but we cannot set a hostname locally: https://javadoc.jenkins-ci.org/hudson/model/Computer.html#getHostName--
        if (nodeHostname != null) {
            expectedTags = Arrays.copyOf(expectedTags, 4);
            expectedTags[3] = "node_hostname:" + nodeHostname;
        }
        
        computerPublisher.doRun();
        
        client.assertMetric("jenkins.node_status.count", 1, hostname, expectedTags);
        client.assertMetric("jenkins.node_status.up", 1, hostname, expectedTags);
        
        // if we set the computer to offline, the metrics should reflect that
        for (Computer computer: jenkins.jenkins.getComputers()){
            computer.setTemporarilyOffline(true, OfflineCause.create(Messages._Hudson_Computer_DisplayName()));
        }
        computerPublisher.doRun();
        client.assertMetric("jenkins.node_status.count", 1, hostname, expectedTags);
        client.assertMetric("jenkins.node_status.up", 0, hostname, expectedTags);
        
    }
    
    @Test
    public void testJenkinsMultipleNodes() throws Exception {
        DatadogClientStub client = new DatadogClientStub();
        DatadogComputerPublisherTestWrapper computerPublisher = new DatadogComputerPublisherTestWrapper();
        ((DatadogComputerPublisherTestWrapper)computerPublisher).setDatadogClient(client);
        
        String url = jenkins.getURL().toString();
        
        EnvVars envVars = new EnvVars();
        envVars.put("HOSTNAME", "test-hostname-2");
        jenkins.createSlave("test", "test", envVars);
        jenkins.createSlave("test1", "test1", envVars);
        
        String[] expectedTags = new String[3];
        expectedTags[0] = "node_name:test";
        expectedTags[1] = "node_label:test";
        expectedTags[2] = "jenkins_url:" + url;
        
        String[] expectedTags1 = new String[3];
        expectedTags1[0] = "node_name:test1";
        expectedTags1[1] = "node_label:test1";
        expectedTags1[2] = "jenkins_url:" + url;
        
        // The CI sets a hostname but we cannot set a hostname locally 
        String nodeHostname = null;
        String hostname = DatadogUtilities.getHostname(null);
        for (Computer computer: jenkins.jenkins.getComputers()){
            nodeHostname = computer.getHostName();
        }
        if (nodeHostname != null) {
            expectedTags = Arrays.copyOf(expectedTags, 4);
            expectedTags1 = Arrays.copyOf(expectedTags1, 4);
            expectedTags[3] = "node_hostname:" + nodeHostname;
            expectedTags1[3] = "node_hostname:" + nodeHostname;
        }
        
        computerPublisher.doRun();
        
        client.assertMetric("jenkins.node_status.count", 1, hostname, expectedTags);
        client.assertMetric("jenkins.node_status.up", 0, hostname, expectedTags);
        
        client.assertMetric("jenkins.node_status.count", 1, hostname, expectedTags1);
        client.assertMetric("jenkins.node_status.up", 0, hostname, expectedTags1);
    }
    
    @Test
    public void testNodeStatusCountAreSame() throws Exception {
        DatadogClientStub client = new DatadogClientStub();
        DatadogComputerPublisherTestWrapper computerPublisher = new DatadogComputerPublisherTestWrapper();
        ((DatadogComputerPublisherTestWrapper)computerPublisher).setDatadogClient(client);
        
        String url = jenkins.getURL().toString();
        
        EnvVars envVars = new EnvVars();
        envVars.put("HOSTNAME", "test-hostname-2");
        jenkins.createSlave("test", "test", envVars);
        jenkins.createSlave("test1", "test1", envVars);
        
        String[] expectedTags = new String[3];
        expectedTags[0] = "node_name:test";
        expectedTags[1] = "node_label:test";
        expectedTags[2] = "jenkins_url:" + url;
        
        String[] expectedTags1 = new String[3];
        expectedTags1[0] = "node_name:test1";
        expectedTags1[1] = "node_label:test1";
        expectedTags1[2] = "jenkins_url:" + url;
        
        String[] expectedTagsGlobal = new String[1];
        expectedTagsGlobal[0] = "jenkins_url:" + url;
    
        // The CI sets a hostname but we cannot set a hostname locally 
        String nodeHostname = null;
        String hostname = DatadogUtilities.getHostname(null);
        for (Computer computer: jenkins.jenkins.getComputers()){
            nodeHostname = computer.getHostName();
        }
        if (nodeHostname != null) {
            expectedTags = Arrays.copyOf(expectedTags, 4);
            expectedTags1 = Arrays.copyOf(expectedTags1, 4);
            expectedTags[3] = "node_hostname:" + nodeHostname;
            expectedTags1[3] = "node_hostname:" + nodeHostname;
        }
        
        computerPublisher.doRun();
        
        int numComputers = 3;
        int numComputersOnline = 1;
        int numComputersOffline = 2;
        client.assertMetric("jenkins.node.count", numComputers, hostname, expectedTagsGlobal);
        client.assertMetricValues("jenkins.node_status.count", 1, hostname, numComputers);
        
        
        client.assertMetric("jenkins.node.online", numComputersOnline, hostname, expectedTagsGlobal);
        client.assertMetricValues("jenkins.node_status.up", 1, hostname, numComputersOnline);
        client.assertMetricValues("jenkins.node_status.up", 0, hostname, numComputersOffline);

    }
}