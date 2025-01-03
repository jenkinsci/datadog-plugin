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

package org.datadog.jenkins.plugins.datadog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.clients.DatadogEventStub;
import org.datadog.jenkins.plugins.datadog.clients.DatadogMetric;
import org.datadog.jenkins.plugins.datadog.listeners.DatadogBuildListener;
import org.datadog.jenkins.plugins.datadog.listeners.DatadogBuildListenerTestWrapper;
import org.datadog.jenkins.plugins.datadog.stubs.ProjectStub;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Test suite for global tags configuration of Jenkins plugin
 *  - Tests for global tags
 *  - Tests for global job tags
 */
public class DatadogGlobalTagsTest {

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private DatadogClientStub client;
    private DatadogBuildListener datadogBuildListener;
    EnvVars envVars;

    @Before
    public void setUpMocks() {
      this.client = new DatadogClientStub();

      this.datadogBuildListener = new DatadogBuildListenerTestWrapper();
      ((DatadogBuildListenerTestWrapper) datadogBuildListener).setDatadogClient(client);


      DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
      cfg.setGlobalJobTags("(.*?)_job, global_job_tag:$ENV_VAR");
      cfg.setGlobalTags("global_tag:$ENV_VAR");

      EnvVars.masterEnvVars.put("ENV_VAR", "value");
    }

    public void assertAllJobMetricsAndEvents(){
      // Assert all *.job.* metrics contain the custom_tag
      for (DatadogMetric metric : this.client.metrics){
        if (metric.getName().contains(".job.")){
          Assert.assertTrue(metric.getTags().contains("global_tag:value"));
          Assert.assertTrue(metric.getTags().contains("global_job_tag:value"));
        }
      }

      // Assert all job related events contain the custom tag
      for (DatadogEventStub event : this.client.events){
        Assert.assertTrue(event.getTags().containsKey("global_tag"));
        Assert.assertTrue(event.getTags().get("global_tag").contains("value"));
        Assert.assertTrue(event.getTags().containsKey("global_job_tag"));
        Assert.assertTrue(event.getTags().get("global_job_tag").contains("value"));
      }
    }

    @Test
    public void testGlobalJobTagsFreestyle() throws Exception {
      Jenkins jenkins = mock(Jenkins.class);
      when(jenkins.getFullName()).thenReturn("");
      ProjectStub job = new ProjectStub(jenkins, "freestyle_job");

      Run run = mock(Run.class);
      when(run.getResult()).thenReturn(null);
      when(run.getParent()).thenReturn(job);
      when(run.getEnvironment(any(TaskListener.class))).thenReturn(new EnvVars());

      this.datadogBuildListener.onInitialize(run);
      assertAllJobMetricsAndEvents();

      this.datadogBuildListener.onStarted(run, mock(TaskListener.class));
      assertAllJobMetricsAndEvents();

      this.datadogBuildListener.onCompleted(run, mock(TaskListener.class));
      assertAllJobMetricsAndEvents();

      this.datadogBuildListener.onFinalized(run);
      assertAllJobMetricsAndEvents();
    }

    @Test
    public void testGlobalJobTagsPipeline() throws Exception {
      jenkinsRule.createOnlineSlave(new LabelAtom("test"));
      WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipeline_job");
      String definition = IOUtils.toString(
          this.getClass().getResourceAsStream("testPipeline.txt"),
          "UTF-8"
      );
      job.setDefinition(new CpsFlowDefinition(definition, true));

      WorkflowRun run = job.scheduleBuild2(0).get();

      this.datadogBuildListener.onInitialize(run);
      assertAllJobMetricsAndEvents();

      this.datadogBuildListener.onStarted(run, mock(TaskListener.class));
      assertAllJobMetricsAndEvents();

      this.datadogBuildListener.onCompleted(run, mock(TaskListener.class));
      assertAllJobMetricsAndEvents();

      this.datadogBuildListener.onFinalized(run);
      assertAllJobMetricsAndEvents();
    }
}
