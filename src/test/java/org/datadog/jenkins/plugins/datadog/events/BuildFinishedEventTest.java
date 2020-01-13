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

package org.datadog.jenkins.plugins.datadog.events;

import hudson.EnvVars;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DatadogUtilities.class, Jenkins.class})
public class BuildFinishedEventTest {

    @Mock
    private Jenkins jenkins;

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);

        PowerMockito.mockStatic(DatadogUtilities.class);
    }

    @Test
    public void testWithNothingSet() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(0l);
        when(DatadogUtilities.getHostname(any(String.class))).thenReturn(null);

        when(jenkins.getFullName()).thenReturn(null);

        Job job = mock(Job.class);
        when(job.getParent()).thenReturn(jenkins);
        when(job.getName()).thenReturn(null);

        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(null);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(null);
        when(run.getParent()).thenReturn(job);

        TaskListener listener = mock(TaskListener.class);
        BuildData bd = new BuildData(run, listener);
        DatadogEvent event = new BuildFinishedEventImpl(bd);

        Assert.assertTrue(event.getHost() == null);
        Assert.assertTrue(event.getAggregationKey().equals("unknown"));
        Assert.assertTrue(event.getDate() == 0);
        Assert.assertTrue(event.getTags().size() == 1);
        Assert.assertTrue(event.getTags().get("job").contains("unknown"));
        Assert.assertTrue(event.getTitle().equals("Job unknown build #0 unknown on unknown"));
        Assert.assertTrue(event.getText().contains("[Job unknown build #0](unknown) finished with status unknown (0.00 secs)"));
        Assert.assertTrue(event.getAlertType().equals(DatadogEvent.AlertType.WARNING));
        Assert.assertTrue(event.getPriority().equals(DatadogEvent.Priority.NORMAL));
    }

    @Test
    public void testWithNothingSet_parentFullName() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(0l);
        when(DatadogUtilities.getHostname(any(String.class))).thenReturn(null);

        when(jenkins.getFullName()).thenReturn("parentFullName");

        Job job = mock(Job.class);
        when(job.getParent()).thenReturn(jenkins);
        when(job.getName()).thenReturn(null);

        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(null);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(null);
        when(run.getParent()).thenReturn(job);

        TaskListener listener = mock(TaskListener.class);
        BuildData bd = new BuildData(run, listener);
        DatadogEvent event = new BuildFinishedEventImpl(bd);

        Assert.assertTrue(event.getAggregationKey().equals("parentFullName/null"));
        Assert.assertTrue(event.getTags().size() == 1);
        Assert.assertTrue(event.getTags().get("job").contains("parentFullName/null"));
        Assert.assertTrue(event.getTitle().equals("Job parentFullName/null build #0 unknown on unknown"));
    }

    @Test
    public void testWithNothingSet_parentFullName_2() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(0l);
        when(DatadogUtilities.getHostname(any(String.class))).thenReturn(null);

        when(jenkins.getFullName()).thenReturn("parent»Full  Name");

        Job job = mock(Job.class);
        when(job.getParent()).thenReturn(jenkins);
        when(job.getName()).thenReturn(null);

        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(null);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(null);
        when(run.getParent()).thenReturn(job);

        TaskListener listener = mock(TaskListener.class);
        BuildData bd = new BuildData(run, listener);
        DatadogEvent event = new BuildFinishedEventImpl(bd);

        Assert.assertTrue(event.getAggregationKey().equals("parent/FullName/null"));
        Assert.assertTrue(event.getTags().size() == 1);
        Assert.assertTrue(event.getTags().get("job").contains("parent/FullName/null"));
        Assert.assertTrue(event.getTitle().equals("Job parent/FullName/null build #0 unknown on unknown"));
    }

    @Test
    public void testWithNothingSet_jobName() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(0l);
        when(DatadogUtilities.getHostname(any(String.class))).thenReturn(null);

        when(jenkins.getFullName()).thenReturn("parentFullName");

        Job job = mock(Job.class);
        when(job.getParent()).thenReturn(jenkins);
        when(job.getName()).thenReturn("jobName");

        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(null);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(null);
        when(run.getParent()).thenReturn(job);

        TaskListener listener = mock(TaskListener.class);
        BuildData bd = new BuildData(run, listener);
        DatadogEvent event = new BuildFinishedEventImpl(bd);

        Assert.assertTrue(event.getHost() == null);
        Assert.assertTrue(event.getDate() == 0);
        Assert.assertTrue(event.getAggregationKey().equals("parentFullName/jobName"));
        Assert.assertTrue(event.getTags().size() == 1);
        Assert.assertTrue(event.getTags().get("job").contains("parentFullName/jobName"));
        Assert.assertTrue(event.getTitle().equals("Job parentFullName/jobName build #0 unknown on unknown"));
    }

    @Test
    public void testWithNothingSet_result_failure() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(0l);
        when(DatadogUtilities.getHostname(any(String.class))).thenReturn(null);

        when(jenkins.getFullName()).thenReturn("parentFullName");

        Job job = mock(Job.class);
        when(job.getParent()).thenReturn(jenkins);
        when(job.getName()).thenReturn("jobName");

        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.FAILURE);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(null);
        when(run.getParent()).thenReturn(job);

        TaskListener listener = mock(TaskListener.class);
        BuildData bd = new BuildData(run, listener);
        DatadogEvent event = new BuildFinishedEventImpl(bd);

        Assert.assertTrue(event.getHost() == null);
        Assert.assertTrue(event.getDate() == 0);
        Assert.assertTrue(event.getAggregationKey().equals("parentFullName/jobName"));
        Assert.assertTrue(event.getTags().size() == 2);
        Assert.assertTrue(event.getTags().get("job").contains("parentFullName/jobName"));
        Assert.assertTrue(event.getTags().get("result").contains("FAILURE"));
        Assert.assertTrue(event.getTitle().equals("Job parentFullName/jobName build #0 failure on unknown"));
        Assert.assertTrue(event.getText().contains("[Job parentFullName/jobName build #0](unknown) finished with status failure (0.00 secs)"));
        Assert.assertTrue(event.getAlertType().equals(DatadogEvent.AlertType.ERROR));
        Assert.assertTrue(event.getPriority().equals(DatadogEvent.Priority.NORMAL));
    }

    @Test
    public void testWithNothingSet_result_unstable() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(0l);
        when(DatadogUtilities.getHostname(any(String.class))).thenReturn(null);

        when(jenkins.getFullName()).thenReturn("parentFullName");

        Job job = mock(Job.class);
        when(job.getParent()).thenReturn(jenkins);
        when(job.getName()).thenReturn("jobName");

        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.UNSTABLE);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(null);
        when(run.getParent()).thenReturn(job);

        TaskListener listener = mock(TaskListener.class);
        BuildData bd = new BuildData(run, listener);
        DatadogEvent event = new BuildFinishedEventImpl(bd);

        Assert.assertTrue(event.getHost() == null);
        Assert.assertTrue(event.getDate() == 0);
        Assert.assertTrue(event.getAggregationKey().equals("parentFullName/jobName"));
        Assert.assertTrue(event.getTags().size() == 2);
        Assert.assertTrue(event.getTags().get("job").contains("parentFullName/jobName"));
        Assert.assertTrue(event.getTags().get("result").contains("UNSTABLE"));
        Assert.assertTrue(event.getTitle().equals("Job parentFullName/jobName build #0 unstable on unknown"));
        Assert.assertTrue(event.getText().contains("[Job parentFullName/jobName build #0](unknown) finished with status unstable (0.00 secs)"));
        Assert.assertTrue(event.getAlertType().equals(DatadogEvent.AlertType.WARNING));
        Assert.assertTrue(event.getPriority().equals(DatadogEvent.Priority.NORMAL));
    }

    @Test
    public void testWithEverythingSet() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        when(DatadogUtilities.getHostname(any(String.class))).thenReturn("test-hostname-1");

        when(jenkins.getFullName()).thenReturn("ParentFullName");

        Job job = mock(Job.class);
        when(job.getParent()).thenReturn(jenkins);
        when(job.getName()).thenReturn("JobName");

        EnvVars envVars = new EnvVars();
        envVars.put("HOSTNAME", "test-hostname-2");
        envVars.put("NODE_NAME", "test-node");
        envVars.put("BUILD_URL", "http://build_url.com");
        envVars.put("GIT_BRANCH", "test-branch");

        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(envVars);
        when(run.getDuration()).thenReturn(10L);
        when(run.getNumber()).thenReturn(2);
        when(run.getParent()).thenReturn(job);

        TaskListener listener = mock(TaskListener.class);

        BuildData bd = new BuildData(run, listener);
        DatadogEvent event = new BuildFinishedEventImpl(bd);

        Assert.assertTrue(event.getHost().equals("test-hostname-1"));
        Assert.assertTrue(event.getAggregationKey().equals("ParentFullName/JobName"));
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertTrue(event.getTags().size() == 4);
        Assert.assertTrue(event.getTags().get("job").contains("ParentFullName/JobName"));
        Assert.assertTrue(event.getTags().get("result").contains("SUCCESS"));
        Assert.assertTrue(event.getTags().get("branch").contains("test-branch"));
        Assert.assertTrue(event.getTags().get("node").contains("test-node"));
        Assert.assertTrue(event.getTitle().equals("Job ParentFullName/JobName build #2 success on test-hostname-1"));
        Assert.assertTrue(event.getText().contains("[Job ParentFullName/JobName build #2](http://build_url.com) finished with status success (0.01 secs)"));
        Assert.assertTrue(event.getAlertType().equals(DatadogEvent.AlertType.SUCCESS));
        Assert.assertTrue(event.getPriority().equals(DatadogEvent.Priority.LOW));

    }

    @Test
    public void testWithEverythingSet_envVarsAndTags() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        when(DatadogUtilities.getHostname(any(String.class))).thenReturn("test-hostname-1");

        when(jenkins.getFullName()).thenReturn("ParentFullName");

        Job job = mock(Job.class);
        when(job.getParent()).thenReturn(jenkins);
        when(job.getName()).thenReturn("JobName");

        EnvVars envVars = new EnvVars();
        envVars.put("BUILD_URL", "http://build_url.com");
        envVars.put("CVS_BRANCH", "csv-branch");
        envVars.put("SVN_BRANCH", "svn-branch");

        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(envVars);
        when(run.getDuration()).thenReturn(10L);
        when(run.getNumber()).thenReturn(2);
        when(run.getParent()).thenReturn(job);

        TaskListener listener = mock(TaskListener.class);

        BuildData bd = new BuildData(run, listener);
        Map<String, Set<String>> tags = new HashMap<>();
        tags = DatadogClientStub.addTagToMap(tags, "tag1", "value1");
        tags = DatadogClientStub.addTagToMap(tags, "tag2", "value2");
        bd.setTags(tags);
        DatadogEvent event = new BuildFinishedEventImpl(bd);

        Assert.assertTrue(event.getHost().equals("test-hostname-1"));
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertTrue(event.getAggregationKey().equals("ParentFullName/JobName"));
        Assert.assertTrue(event.getTags().size() == 5);
        Assert.assertTrue(event.getTags().get("job").contains("ParentFullName/JobName"));
        Assert.assertTrue(event.getTags().get("result").contains("SUCCESS"));
        Assert.assertTrue(event.getTags().get("tag1").contains("value1"));
        Assert.assertTrue(event.getTags().get("tag2").contains("value2"));
        Assert.assertTrue(event.getTags().get("branch").contains("csv-branch"));
        Assert.assertTrue(event.getTitle().equals("Job ParentFullName/JobName build #2 success on test-hostname-1"));
        Assert.assertTrue(event.getText().contains("[Job ParentFullName/JobName build #2](http://build_url.com) finished with status success (0.01 secs)"));
        Assert.assertTrue(event.getAlertType().equals(DatadogEvent.AlertType.SUCCESS));
        Assert.assertTrue(event.getPriority().equals(DatadogEvent.Priority.LOW));
    }
}
