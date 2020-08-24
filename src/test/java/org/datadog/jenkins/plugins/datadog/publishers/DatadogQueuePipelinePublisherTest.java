package org.datadog.jenkins.plugins.datadog.publishers;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import org.apache.commons.io.IOUtils;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import hudson.EnvVars;
import hudson.model.Messages;
import hudson.model.Computer;
import hudson.slaves.OfflineCause;

public class DatadogQueuePipelinePublisherTest {
    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();


    @Test
    public void testPipelineInQueue() throws Exception {
        DatadogClientStub client = new DatadogClientStub();;
        ClientFactory.setTestClient(client);
        DatadogQueuePublisher queuePublisher = new DatadogQueuePublisher();
        String hostname = DatadogUtilities.getHostname(null);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationQueue");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipeline.txt"),
                "UTF-8"
        );
        job.setDefinition(new CpsFlowDefinition(definition, true));
        String displayName = job.getDisplayName();

        // schedule build and wait for it to get queued
        job.scheduleBuild2(0);
        Thread.sleep(1000);

        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:" + displayName;
        queuePublisher.doRun();
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags);
    }

}
