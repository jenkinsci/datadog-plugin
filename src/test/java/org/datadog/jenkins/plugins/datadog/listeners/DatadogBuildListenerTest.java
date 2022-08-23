/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package org.datadog.jenkins.plugins.datadog.listeners;

import com.cloudbees.workflow.rest.external.StageNodeExt;
import hudson.EnvVars;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogEvent.AlertType;
import org.datadog.jenkins.plugins.datadog.DatadogEvent.Priority;
import org.datadog.jenkins.plugins.datadog.stubs.BuildStub;
import org.datadog.jenkins.plugins.datadog.stubs.ProjectStub;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.clients.DatadogMetric;
import org.datadog.jenkins.plugins.datadog.stubs.QueueStub;
import org.datadog.jenkins.plugins.datadog.stubs.RunExtStub;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DatadogBuildListenerTest {

    private DatadogClientStub client;
    private DatadogBuildListener datadogBuildListener;
    private ProjectStub job;
    private Queue queue;
    private WorkflowRun workflowRun;
    EnvVars envVars;
    
    @Before
    public void setUpMocks() {
        this.client = new DatadogClientStub();

        this.datadogBuildListener = new DatadogBuildListenerTestWrapper();
        ((DatadogBuildListenerTestWrapper)datadogBuildListener).setDatadogClient(client);

        this.queue = new QueueStub(mock(LoadBalancer.class));
        Queue.Item item = mock(Queue.Item.class);
        when(item.getId()).thenReturn(1L);
        when(item.getInQueueSince()).thenReturn(2000000L);
        ((QueueStub)queue).setItem(item);
        ((DatadogBuildListenerTestWrapper)datadogBuildListener).setQueue(queue);

        Jenkins jenkins = mock(Jenkins.class);
        when(jenkins.getFullName()).thenReturn("ParentFullName");
        this.job = new ProjectStub(jenkins,"JobName");

        this.envVars = new EnvVars();
        envVars.put("HOSTNAME", "test-hostname-2");
        envVars.put("NODE_NAME", "test-node");
        envVars.put("BUILD_URL", "http://build_url.com");
        envVars.put("GIT_BRANCH", "test-branch");

        workflowRun = mock(WorkflowRun.class);

    }

    @Test
    public void testOnCompletedWithNothing() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        when(jenkins.getFullName()).thenReturn(null);
        this.job = new ProjectStub(jenkins,null);

        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(null);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(this.envVars);
        when(run.getParent()).thenReturn(this.job);

        this.datadogBuildListener.onCompleted(run, mock(TaskListener.class));
        this.client.assertedAllMetricsAndServiceChecks();
    }

    @Test
    public void testOnCompletedOnSuccessfulRun() throws Exception {
        BuildStub previousSuccessfulRun = new BuildStub(this.job, Result.SUCCESS, envVars, null,
                121000L, 1, null, 1000000L, null);

        BuildStub previousFailedRun1 = new BuildStub(this.job, Result.FAILURE, envVars, previousSuccessfulRun,
                122000L, 2, previousSuccessfulRun, 2000000L, null);

        BuildStub previousFailedRun2 = new BuildStub(this.job, Result.FAILURE, envVars, previousSuccessfulRun,
                123000L, 3, previousFailedRun1, 3000000L, null);

        BuildStub successRun = new BuildStub(this.job, Result.SUCCESS, envVars, previousSuccessfulRun,
                124000L, 4, previousFailedRun2, 4000000L, null);

        datadogBuildListener.onCompleted(previousSuccessfulRun, mock(TaskListener.class));
        String[] scExpectedTags1 = new String[]{
            "job:ParentFullName/JobName", "node:test-node", "user_id:anonymous", "jenkins_url:unknown", "branch:test-branch"
        };
        String[] metricExpectedTags1 = new String[6];
        System.arraycopy(scExpectedTags1, 0, metricExpectedTags1, 0, 5);
        metricExpectedTags1[5] = "result:SUCCESS";
        client.assertMetric("jenkins.job.duration", 121, "test-hostname-2", metricExpectedTags1);
        client.assertMetric("jenkins.job.leadtime", 121, "test-hostname-2", metricExpectedTags1);
        client.assertServiceCheck("jenkins.job.status", 0, "test-hostname-2", scExpectedTags1);
        client.assertEvent("Job ParentFullName/JobName build #1 success on test-hostname-2",
                Priority.LOW, AlertType.SUCCESS, 1121L);

        datadogBuildListener.onCompleted(previousFailedRun1, mock(TaskListener.class));
        String[] metricExpectedTags2 = new String[6];
        System.arraycopy(scExpectedTags1, 0, metricExpectedTags2, 0, 5);
        metricExpectedTags2[5] = "result:FAILURE";
        client.assertMetric("jenkins.job.duration", 122, "test-hostname-2", metricExpectedTags2);
        client.assertMetric("jenkins.job.feedbacktime", 122, "test-hostname-2", metricExpectedTags2);
        client.assertServiceCheck("jenkins.job.status", 2, "test-hostname-2", scExpectedTags1);
        client.assertEvent("Job ParentFullName/JobName build #2 failure on test-hostname-2",
                Priority.NORMAL, AlertType.ERROR, 2122L);

        datadogBuildListener.onCompleted(previousFailedRun2, mock(TaskListener.class));
        client.assertMetric("jenkins.job.duration", 123, "test-hostname-2", metricExpectedTags2);
        client.assertMetric("jenkins.job.feedbacktime", 123, "test-hostname-2", metricExpectedTags2);
        client.assertMetric("jenkins.job.completed", 2, "test-hostname-2", metricExpectedTags2);
        client.assertServiceCheck("jenkins.job.status", 2, "test-hostname-2", scExpectedTags1);
        client.assertEvent("Job ParentFullName/JobName build #3 failure on test-hostname-2",
                Priority.NORMAL, AlertType.ERROR, 3123L);

        datadogBuildListener.onCompleted(successRun, mock(TaskListener.class));
        client.assertMetric("jenkins.job.duration", 124, "test-hostname-2", metricExpectedTags1);
        client.assertMetric("jenkins.job.leadtime", 2124, "test-hostname-2", metricExpectedTags1);
        client.assertMetric("jenkins.job.cycletime", (4000+124)-(1000+121), "test-hostname-2", metricExpectedTags1);
        client.assertMetric("jenkins.job.mttr", 4000-2000, "test-hostname-2", metricExpectedTags1);
        client.assertServiceCheck("jenkins.job.status", 0, "test-hostname-2", scExpectedTags1);
        client.assertMetric("jenkins.job.completed", 2, "test-hostname-2", metricExpectedTags1);
        client.assertEvent("Job ParentFullName/JobName build #4 success on test-hostname-2",
                Priority.LOW, AlertType.SUCCESS, 4124L);
        client.assertedAllMetricsAndServiceChecks();
        client.assertedAllEvents();
    }

    @Test
    public void testOnCompletedWorkflowRun() throws Exception {
        final int stageCount = 5;
        final long stageDuration = 12000;
        final long pauseDurationPerStage = 400;
        final long buildDuration = stageDuration * stageCount;
        final long pauseDuration = pauseDurationPerStage * stageCount;
        final long totalDuration = buildDuration + pauseDuration;
        final String[] stageNames = {"Stage 1: Checkout SCM", "Stage 2", "Stage 3", "Stage 4", "Stage 5"};

        WorkflowJob job = mock(WorkflowJob.class);
        when(job.getFullName()).thenReturn("Pipeline job");
        when(workflowRun.getParent()).thenReturn(job);

        TaskListener listener = mock(TaskListener.class);
        when(workflowRun.getEnvironment(listener)).thenReturn(this.envVars);
        when(workflowRun.getStartTimeInMillis()).thenReturn((long)0);
        when(workflowRun.getDuration()).thenReturn(totalDuration);
        when(workflowRun.getResult()).thenReturn(null);
        when(workflowRun.getNumber()).thenReturn(0);
        when(workflowRun.getResult()).thenReturn(Result.SUCCESS);

        RunExtStub runExt = new RunExtStub();
        for(int i = 0; i < stageCount; i++){
            StageNodeExt stage = mock(StageNodeExt.class);
            when(stage.getName()).thenReturn(stageNames[i]);
            when(stage.getPauseDurationMillis()).thenReturn(pauseDurationPerStage);
            when(stage.getDurationMillis()).thenReturn(stageDuration);
            runExt.addStage(stage);
        }
        ((DatadogBuildListenerTestWrapper)datadogBuildListener).setStubbedRunExt(runExt);

        datadogBuildListener.onCompleted(workflowRun, listener);

        String[] expectedTags = {
                "result:SUCCESS",
                "node:test-node",
                "jenkins_url:unknown",
                "user_id:anonymous",
                "job:Pipelinejob",
                "branch:test-branch"
        };
        client.assertMetric("jenkins.job.duration", totalDuration / 1000, "test-hostname-2", expectedTags);
        client.assertMetric("jenkins.job.pause_duration", pauseDuration / 1000, "test-hostname-2", expectedTags);
        client.assertMetric("jenkins.job.build_duration", buildDuration / 1000, "test-hostname-2", expectedTags);
        client.assertMetric("jenkins.job.completed", 1, "test-hostname-2", expectedTags);
        client.assertMetric("jenkins.job.leadtime", totalDuration / 1000, "test-hostname-2", expectedTags);
    }

    @Test
    public void testOnCompletedOnFailedRun() throws Exception {
        BuildStub previousSuccessfulRun = new BuildStub(this.job, Result.SUCCESS, envVars, null,
                123000L, 1, null, 1000000L, null);

        BuildStub failedRun = new BuildStub(this.job, Result.FAILURE, envVars, null,
                124000L, 2, null, 2000000L, previousSuccessfulRun);

        datadogBuildListener.onCompleted(previousSuccessfulRun, mock(TaskListener.class));
        String[] scExpectedTags = new String[]{
                "job:ParentFullName/JobName", "node:test-node", "user_id:anonymous", "jenkins_url:unknown", "branch:test-branch"
        };
        String[] metricExpectedTags1 = new String[6];
        System.arraycopy(scExpectedTags, 0, metricExpectedTags1, 0, 5);
        metricExpectedTags1[5] = "result:SUCCESS";
        client.assertMetric("jenkins.job.duration", 123, "test-hostname-2", metricExpectedTags1);
        client.assertMetric("jenkins.job.leadtime", 123, "test-hostname-2", metricExpectedTags1);
        client.assertMetric("jenkins.job.completed", 1, "test-hostname-2", metricExpectedTags1);
        client.assertServiceCheck("jenkins.job.status", 0, "test-hostname-2", scExpectedTags);
        client.assertEvent("Job ParentFullName/JobName build #1 success on test-hostname-2",
                Priority.LOW, AlertType.SUCCESS, 1123L);
        client.assertedAllMetricsAndServiceChecks();

        datadogBuildListener.onCompleted(failedRun, mock(TaskListener.class));
        String[] metricExpectedTags2 = new String[6];
        System.arraycopy(scExpectedTags, 0, metricExpectedTags2, 0, 5);
        metricExpectedTags2[5] = "result:FAILURE";
        client.assertMetric("jenkins.job.duration", 124, "test-hostname-2", metricExpectedTags2);
        client.assertMetric("jenkins.job.mtbf", 1000, "test-hostname-2", metricExpectedTags2);
        client.assertMetric("jenkins.job.feedbacktime", 124, "test-hostname-2", metricExpectedTags2);
        client.assertMetric("jenkins.job.completed", 1, "test-hostname-2", metricExpectedTags2);
        client.assertServiceCheck("jenkins.job.status", 2, "test-hostname-2", scExpectedTags);
        client.assertEvent("Job ParentFullName/JobName build #2 failure on test-hostname-2",
                Priority.NORMAL, AlertType.ERROR, 2124L);
        client.assertedAllMetricsAndServiceChecks();
        client.assertedAllEvents();
    }

    @Test
    public void testEvents() throws Exception {
        BuildStub previousSuccessfulRun = new BuildStub(this.job, Result.SUCCESS, envVars, null,
                123000L, 1, null, 1000000L, null);

        BuildStub failedRun = new BuildStub(this.job, Result.FAILURE, envVars, null,
                124000L, 2, null, 2000000L, previousSuccessfulRun);

        datadogBuildListener.onCompleted(previousSuccessfulRun, mock(TaskListener.class));
        client.assertEvent("Job ParentFullName/JobName build #1 success on test-hostname-2",
                Priority.LOW, AlertType.SUCCESS, 1123L);

        datadogBuildListener.onCompleted(failedRun, mock(TaskListener.class));
        client.assertEvent("Job ParentFullName/JobName build #2 failure on test-hostname-2",
                Priority.NORMAL, AlertType.ERROR, 2124L);
        client.assertedAllEvents();

        // build has already completed, no new events will be sent
        datadogBuildListener.onDeleted(failedRun);
        client.assertedAllEvents();

        BuildStub notBuiltRun = new BuildStub(this.job, Result.NOT_BUILT, envVars, null,
                124000L, 3, null, 2000000L, null);
        datadogBuildListener.onDeleted(notBuiltRun);
        client.assertEvent("Job ParentFullName/JobName build #3 aborted on test-hostname-2",
                Priority.LOW, AlertType.INFO, 2124L);
        client.assertedAllEvents();

        BuildStub abortedRun = new BuildStub(this.job, Result.ABORTED, envVars, null,
                124000L, 4, null, 2000000L, null);
        datadogBuildListener.onDeleted(abortedRun);
        client.assertEvent("Job ParentFullName/JobName build #4 aborted on test-hostname-2",
                Priority.LOW, AlertType.INFO, 2124L);
        client.assertedAllEvents();
    }

    @Test
    public void testOnStarted() throws Exception {
        BuildStub run = new BuildStub(this.job, Result.SUCCESS, envVars, null,
                123000L, 1, null, 1000000L, null);

        datadogBuildListener.onStarted(run, mock(TaskListener.class));
        String[] expectedTags = new String[6];
        expectedTags[0] = "job:ParentFullName/JobName";
        expectedTags[1] = "node:test-node";
        expectedTags[2] = "result:SUCCESS";
        expectedTags[3] = "user_id:anonymous";
        expectedTags[4] = "jenkins_url:unknown";
        expectedTags[5] = "branch:test-branch";
        client.assertMetric("jenkins.job.started", 1, "test-hostname-2", expectedTags);
        Assert.assertTrue(client.metrics.size() == 1);
        DatadogMetric metric = client.metrics.get(0);
        Assert.assertTrue(metric.getName().equals("jenkins.job.waiting"));
        Assert.assertTrue(metric.getValue() > 0);
        Assert.assertTrue(metric.getHostname().equals("test-hostname-2"));
        Assert.assertTrue(metric.getTags().containsAll(Arrays.asList(expectedTags)));
        client.metrics.clear();
        client.assertedAllMetricsAndServiceChecks();
    }

}
