package org.datadog.jenkins.plugins.datadog.publishers;

import java.util.Arrays;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.EnvVars;
import hudson.model.Messages;
import hudson.model.Computer;
import hudson.slaves.OfflineCause;

public class DatadogComputerPublisherTest {
    @ClassRule 
    public static JenkinsRule jenkins = new JenkinsRule();
    private static DatadogClientStub client = new DatadogClientStub();
    
    @BeforeClass
    public static void setup() throws Exception {
        ClientFactory.setTestClient(client);
    }
    
    @Test
    public void testJenkinsNodeMetrics() throws Exception {
        DatadogComputerPublisher computerPublisher = new DatadogComputerPublisher();
        
        String url = jenkins.getURL().toString();
        String hostname = DatadogUtilities.getHostname(null);
        
        String[] expectedTags = new String[3];
        expectedTags[0] = "node_name:master";
        expectedTags[1] = "node_label:built-in";
        expectedTags[2] = "jenkins_url:" + url;
        
        // The CI sets a hostname but we cannot set a hostname locally: https://javadoc.jenkins-ci.org/hudson/model/Computer.html#getHostName--
        String nodeHostname = jenkins.jenkins.getComputer("").getHostName();
        if (nodeHostname != null) {
            expectedTags = Arrays.copyOf(expectedTags, 4);
            expectedTags[3] = "node_hostname:" + nodeHostname;
        }
        
        computerPublisher.doRun();
        
        client.assertMetric("jenkins.node_status.count", 1, hostname, expectedTags);
        client.assertMetric("jenkins.node_status.up", 1, hostname, expectedTags);
        client.assertMetric("jenkins.executor.count", 2, hostname, expectedTags);
        client.assertMetric("jenkins.executor.in_use", 0, hostname, expectedTags);
        // All of the executors are online, so jenkins.executor.count == jenkins.executor.in_use + jenkins.executor.free
        client.assertMetric("jenkins.executor.free", 2, hostname, expectedTags);
        
        // if we set the computer to offline, the metrics should reflect that
        for (Computer computer: jenkins.jenkins.getComputers()){
            computer.setTemporarilyOffline(true, OfflineCause.create(Messages._Hudson_Computer_DisplayName()));
        }
        computerPublisher.doRun();
        client.assertMetric("jenkins.node_status.count", 1, hostname, expectedTags);
        client.assertMetric("jenkins.node_status.up", 0, hostname, expectedTags);
        client.assertMetric("jenkins.executor.count", 0, hostname, expectedTags);
        client.assertMetric("jenkins.executor.in_use", 0, hostname, expectedTags);
        client.assertMetric("jenkins.executor.free", 0, hostname, expectedTags);
    }
    
    @Test
    public void testJenkinsMultipleNodes() throws Exception {
        DatadogComputerPublisher computerPublisher = new DatadogComputerPublisher();
        
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
        
        String hostname = DatadogUtilities.getHostname(null);
        
        computerPublisher.doRun();
        
        client.assertMetric("jenkins.node_status.count", 1, hostname, expectedTags);
        client.assertMetric("jenkins.node_status.up", 0, hostname, expectedTags);
        
        client.assertMetric("jenkins.node_status.count", 1, hostname, expectedTags1);
        client.assertMetric("jenkins.node_status.up", 0, hostname, expectedTags1);
    }
    
    @Test
    public void testNodeStatusCountAreSame() throws Exception {
        DatadogComputerPublisher computerPublisher = new DatadogComputerPublisher();
        String url = jenkins.getURL().toString();
        jenkins.jenkins.getComputer("").setTemporarilyOffline(false, OfflineCause.create(Messages._Hudson_Computer_DisplayName()));

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
    
        String hostname = DatadogUtilities.getHostname(null);
        
        computerPublisher.doRun();
        int numComputers = 0;
        int numComputersOnline = 0;
        int numComputersOffline = 0;
        for (Computer computer: jenkins.jenkins.getComputers()){
            numComputers++;
            if (computer.isOffline()) {
                numComputersOffline++;
            }   
            if (computer.isOnline()) {
                numComputersOnline++;
            }
        }
        
        client.assertMetric("jenkins.node.count", numComputers, hostname, expectedTagsGlobal);
        client.assertMetricValues("jenkins.node_status.count", 1, hostname, numComputers);
        
        client.assertMetric("jenkins.node.online", numComputersOnline, hostname, expectedTagsGlobal);
        client.assertMetricValues("jenkins.node_status.up", 1, hostname, numComputersOnline);
        client.assertMetricValues("jenkins.node_status.up", 0, hostname, numComputersOffline);

    }
}