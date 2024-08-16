package org.datadog.jenkins.plugins.datadog.publishers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Messages;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.StringParameterValue;
import hudson.slaves.OfflineCause;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.clients.DatadogMetric;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

public class DatadogQueuePublisherTest {
    @ClassRule 
    public static JenkinsRule jenkins = new JenkinsRule();
    public DatadogClientStub client;
    DatadogQueuePublisher queuePublisher = new DatadogQueuePublisher();
    
    @Before
    public void setup() throws Exception {
        client = new DatadogClientStub();
        queuePublisher = new DatadogQueuePublisher();
        ClientHolder.setClient(client);
        jenkins.jenkins.getQueue().clear();
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

        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:" + displayName;
        queuePublisher.doRun();
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags);
    }

    @Test
    public void testQueuePipeline() throws Exception {
        String hostname = DatadogUtilities.getHostname(null);
        final WorkflowJob project = jenkins.createProject(WorkflowJob.class);
        String displayName = project.getDisplayName();

        project.scheduleBuild(10000, new Cause.RemoteCause("host", "0"));

        // set all the computers offline so they can't execute any builds, filling up the queue
        for (Computer computer: jenkins.jenkins.getComputers()){
            computer.setTemporarilyOffline(true, OfflineCause.create(Messages._Hudson_Computer_DisplayName()));
        }

        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:" + displayName;
        queuePublisher.doRun();
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags);
    }

    @Test
    public void testQueueMetricsJobFiltering() throws Exception {
        DatadogGlobalConfiguration globalConfiguration = DatadogUtilities.getDatadogGlobalDescriptor();
        assertNotNull(globalConfiguration);

        String previousExcludedPattern = globalConfiguration.getExcluded();
        try {
            String filteredJobName = "filtered-project";
            globalConfiguration.setExcluded(filteredJobName);

            final FreeStyleProject project = jenkins.createFreeStyleProject("filtered-project");
            project.getBuildersList().add(new SleepBuilder(10000));

            jenkins.jenkins.getQueue().schedule(project);

            // set all the computers offline so they can't execute any builds, filling up the queue
            for (Computer computer: jenkins.jenkins.getComputers()){
                computer.setTemporarilyOffline(true, OfflineCause.create(Messages._Hudson_Computer_DisplayName()));
            }

            queuePublisher.doRun();

            List<DatadogMetric> filteredJobMetrics = client.getMetrics().stream().filter(metric -> metric.getTags().contains("job_name:" + filteredJobName)).collect(Collectors.toList());
            assertEquals("Did not expect any metrics for filtered job, found: " + filteredJobMetrics, 0, filteredJobMetrics.size());
        } finally {
            globalConfiguration.setExcluded(previousExcludedPattern);
        }
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
        
        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:" + displayName;
        queuePublisher.doRun();
        
        // Since the same job is in the queue multiple times, then its metric should be submitted multiple times
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags);
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
            
        final String[] expectedTags = new String[2];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        expectedTags[1] = "job_name:test7";
        
        final String[] expectedTags1 = Arrays.copyOf(expectedTags, 2);
        expectedTags1[1] = "job_name:test8";
        final String[] expectedTags2 = Arrays.copyOf(expectedTags, 2);
        expectedTags2[1] = "job_name:test9";

        
        queuePublisher.doRun();
        // Make sure metrics are submitted for all jobs when there are multiple jobs.
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags);
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags1);
        client.assertMetric("jenkins.queue.job.in_queue", 1, hostname, expectedTags2);
        
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

    @Test
    public void testAllQueueMetrics() throws Exception {
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
                
        final String[] expectedTags = new String[1];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        
        queuePublisher.doRun();
    
        Queue queueStats = jenkins.jenkins.getQueue();
        int size = queueStats.getItems().length;
        int buildable = queueStats.countBuildableItems();
        int pending = queueStats.getPendingItems().size();
        
        // Make sure values are consistent across jenkins.queue.* and jenkins.queue.job.* metrics
        client.assertMetric("jenkins.queue.size", size, hostname, expectedTags);
        client.assertMetricValuesMin("jenkins.queue.job.in_queue", 1, hostname, size);
        
        client.assertMetric("jenkins.queue.buildable", size, hostname, expectedTags);
        client.assertMetricValuesMin("jenkins.queue.job.buildable", 1, hostname, buildable);
        
        client.assertMetric("jenkins.queue.pending", pending, hostname, expectedTags);
        client.assertMetricValues("jenkins.queue.job.pending", 1, hostname, pending);
        client.assertMetricValuesMin("jenkins.queue.job.pending", 0, hostname, size);

    }
}
