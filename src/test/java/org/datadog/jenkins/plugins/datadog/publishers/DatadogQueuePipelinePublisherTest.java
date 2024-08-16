package org.datadog.jenkins.plugins.datadog.publishers;

import hudson.model.Queue;
import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import org.apache.commons.io.IOUtils;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;


public class DatadogQueuePipelinePublisherTest {
    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();


    @Test
    public void testPipelineInQueue() throws Exception {
        DatadogClientStub client = new DatadogClientStub();;
        ClientHolder.setClient(client);
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
        QueueTaskFuture<WorkflowRun> task = job.scheduleBuild2(0);
        Queue queue = jenkins.jenkins.getQueue();
        for (int i = 0; i < 10; i++) {
            Thread.sleep(500);
            if (!queue.getBuildableItems().isEmpty()) {
                String name  = queue.getBuildableItems().get(0).task.getFullDisplayName();
                if (name != null & name.contains("pipelineIntegrationQueue")) {
                    break;
                }
            }
        }

        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:" + displayName;
        queuePublisher.doRun();
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags);
        task.cancel(true);
    }

}
