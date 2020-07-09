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
import hudson.ExtensionList;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.stubs.BuildStub;
import org.datadog.jenkins.plugins.datadog.stubs.ProjectStub;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.clients.DatadogMetric;
import org.datadog.jenkins.plugins.datadog.stubs.QueueStub;
import org.datadog.jenkins.plugins.datadog.stubs.RunExtStub;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

import java.util.Arrays;
import java.util.SortedMap;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class DatadogBuildListenerExcludeTest {

    private DatadogClientStub client;
    private DatadogBuildListener datadogBuildListener;
    private ProjectStub job;
    static DatadogGlobalConfiguration cfg;
    EnvVars envVars;
    
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    
    
    @Before
    public void setUpMocks() {
        this.client = new DatadogClientStub();

        this.datadogBuildListener = new DatadogBuildListenerTestWrapper();
        ((DatadogBuildListenerTestWrapper)datadogBuildListener).setDatadogClient(client);

        this.envVars = new EnvVars();

    }

    @Test
    public void testExcludeJobs() throws Exception {
        cfg = DatadogUtilities.getDatadogGlobalDescriptor();

        final FreeStyleProject project = j.createFreeStyleProject();
        String displayName = project.getDisplayName();
        //cfg.setExcluded(displayName);

        project.getBuildersList().add(new SleepBuilder(1));
        project.scheduleBuild2(1);
        RunMap runs = project._getRuns();
        SortedMap runs2 = runs.getLoadedBuilds();
        Run run = runs.oldestValue();
        
        
        datadogBuildListener.onCompleted(run, mock(TaskListener.class));
        
        /*
        this.job = new ProjectStub(jenkins, "TestingExclude");
        BuildStub run = new BuildStub(this.job, Result.SUCCESS, envVars, null,
                123000L, 1, null, 1000000L, null);
    
        datadogBuildListener.onCompleted(run, mock(TaskListener.class));
        client.assertMetric("jenkins.job.duration", 122, "test-hostname-2", new String[0]);

        client.assertedAllMetricsAndServiceChecks();
        
        //cfg.setIncluded("TestingExclude");
        this.job = new ProjectStub(jenkins, "TestingExclude");
        BuildStub run1 = new BuildStub(this.job, Result.SUCCESS, envVars, null,
                123000L, 1, null, 1000000L, null);
    
        datadogBuildListener.onCompleted(run1, mock(TaskListener.class));
        */

        client.assertedAllMetricsAndServiceChecks();
    }

}