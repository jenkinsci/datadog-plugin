package org.datadog.jenkins.plugins.datadog.publishers;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

import java.util.Arrays;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;

import hudson.model.Messages;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.slaves.OfflineCause;

public class DatadogQueuePublisherTest {
    @ClassRule 
    public static JenkinsRule jenkins = new JenkinsRule();
    private static DatadogClientStub client = new DatadogClientStub();
    
    @BeforeClass
    public static void setup() throws Exception {
        ClientFactory.setTestClient(client);
    }

    @Test
    public void testQueueMetrics() throws Exception {
        String hostname = DatadogUtilities.getHostname(null);
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        String displayName = project.getDisplayName();
        project.getBuildersList().add(new SleepBuilder(10000));
        
        jenkins.jenkins.getQueue().schedule(project);

        // set all the computers offline so they can't execute any buils, filling up the queue
        for (Computer computer: jenkins.jenkins.getComputers()){
            computer.setTemporarilyOffline(true, OfflineCause.create(Messages._Hudson_Computer_DisplayName()));
        }
        DatadogQueuePublisher queuePublisher = new DatadogQueuePublisher();

        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:" + displayName;
        queuePublisher.doRun();
        client.assertMetric("jenkins.queue.job.size", 1, hostname, expectedTags);
    
    }
    
    @Test
    public void testQueueMetricsMultipleBuilds() throws Exception {
        String hostname = DatadogUtilities.getHostname(null);
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        String displayName = project.getDisplayName();
        project.getBuildersList().add(new SleepBuilder(10000));
        
        for (int i = 0; i < 10; i++) {
            project.scheduleBuild(0, new Cause.RemoteCause("host",String.valueOf(i)), new ParametersAction(new StringParameterValue("param", String.valueOf(i))));
        }

        // set all the computers offline so they can't execute any builds, filling up the queue
        for (Computer computer: jenkins.jenkins.getComputers()){
            computer.setTemporarilyOffline(true, OfflineCause.create(Messages._Hudson_Computer_DisplayName()));
        }
        
        DatadogQueuePublisher queuePublisher = new DatadogQueuePublisher();
        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:" + displayName;
        queuePublisher.doRun();
        
        // Since the same job is in the queue multiple times, then its metric should be submitted multiple times
        client.assertMetric("jenkins.queue.job.size", 1, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.size", 1, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.size", 1, hostname, expectedTags);
    }
    
    @Test
    public void testQueueMetricsMultipleProjects() throws Exception {
        String hostname = DatadogUtilities.getHostname(null);
       
        String displayName = "";
        for (int i = 0; i < 10; i++) {
            FreeStyleProject project = jenkins.createFreeStyleProject();
            displayName = project.getDisplayName();
            project.getBuildersList().add(new SleepBuilder(10000));
            project.scheduleBuild(0, new Cause.RemoteCause("host",String.valueOf(i)), new ParametersAction(new StringParameterValue("param", String.valueOf(i))));
        }

        // set all the computers offline so they can't execute any builds, filling up the queue
        for (Computer computer: jenkins.jenkins.getComputers()){
            computer.setTemporarilyOffline(true, OfflineCause.create(Messages._Hudson_Computer_DisplayName()));
        }
        
        DatadogQueuePublisher queuePublisher = new DatadogQueuePublisher();
        
        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:test7";
        
        final String[] expectedTags1 = Arrays.copyOf(expectedTags, 2);
        expectedTags1[1] = "job_name:test8";
        final String[] expectedTags2 = Arrays.copyOf(expectedTags, 2);
        expectedTags2[1] = "job_name:test9";

        
        queuePublisher.doRun();
        // Make sure metrics are submitted for all jobs when there are multiple jobs.
        client.assertMetric("jenkins.queue.job.size", 1, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.size", 1, hostname, expectedTags1);
        client.assertMetric("jenkins.queue.job.size", 1, hostname, expectedTags2);
        
        client.assertMetric("jenkins.queue.job.pending", 0, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.pending", 0, hostname, expectedTags1);
        client.assertMetric("jenkins.queue.job.pending", 0, hostname, expectedTags2);
        
        client.assertMetric("jenkins.queue.job.stuck", 1, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.stuck", 1, hostname, expectedTags1);
        client.assertMetric("jenkins.queue.job.stuck", 1, hostname, expectedTags2);
        
        client.assertMetric("jenkins.queue.job.blocked", 0, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.blocked", 0, hostname, expectedTags1);
        client.assertMetric("jenkins.queue.job.blocked", 0, hostname, expectedTags2);
        
        client.assertMetric("jenkins.queue.job.buildable", 1, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.buildable", 1, hostname, expectedTags1);
        client.assertMetric("jenkins.queue.job.buildable", 1, hostname, expectedTags2);
    }
}