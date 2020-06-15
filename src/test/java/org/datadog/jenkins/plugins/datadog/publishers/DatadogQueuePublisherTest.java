package org.datadog.jenkins.plugins.datadog.publishers;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;

import hudson.model.Messages;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.slaves.OfflineCause;

public class DatadogQueuePublisherTest {
    @Rule 
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testQueueMetrics() throws Exception {
        String hostname = DatadogUtilities.getHostname(null);
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        String displayName = project.getDisplayName();
        project.getBuildersList().add(new SleepBuilder(10000));
        
        for (int i = 0; i < 100; i++) {
            jenkins.jenkins.getQueue().schedule(project);
        }

        // set all the computers offline so they can't execute any buils, filling up the queue
        for (Computer computer: jenkins.jenkins.getComputers()){
            computer.setTemporarilyOffline(true, OfflineCause.create(Messages._Hudson_Computer_DisplayName()));
        }
        DatadogClientStub client = new DatadogClientStub();
        DatadogQueuePublisherTestWrapper queuePublisher = new DatadogQueuePublisherTestWrapper();
        ((DatadogQueuePublisherTestWrapper)queuePublisher).setDatadogClient(client);

        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:" + displayName;
        queuePublisher.doRun();
        client.assertMetric("jenkins.queue.job.size", 1, hostname, expectedTags);
    
    }
}