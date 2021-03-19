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
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.stubs.BuildStub;
import org.datadog.jenkins.plugins.datadog.stubs.ProjectStub;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;


import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.model.BuildData;

public class DatadogBuildTagTest {

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testGlobalJobTagsFreestyle() throws Exception {

        Jenkins jenkins = mock(Jenkins.class);
        when(jenkins.getFullName()).thenReturn("");
        ProjectStub job = new ProjectStub(jenkins,"freestyle_job");

        EnvVars envVars = new EnvVars();
        envVars.put("ENV_VAR", "value");
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setGlobalJobTags("(.*?)_job, custom_tag:$ENV_VAR");


        Run run = new BuildStub(job, Result.SUCCESS, envVars, null, 10L, 2, null, 0L, null);
        TaskListener listener = mock(TaskListener.class);

        BuildData bd = new BuildData(run, listener);
        DatadogEvent event = new BuildFinishedEventImpl(bd);

        System.out.println(String.format("tags: %s", event.getTags()));
        System.out.println(event.getTags());
        Assert.assertTrue(event.getTags().get("custom_tag").contains("value"));
    }

    // @Test
    // public void testGlobalJobTagsPipeline() throws Exception {

    //     Jenkins jenkins = mock(Jenkins.class);
    //     when(jenkins.getFullName()).thenReturn("");
    //     ProjectStub job = new ProjectStub(jenkins,"JobName");

    //     EnvVars envVars = new EnvVars();
    //     envVars.put("ENV_VAR", "value");
    //     DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
    //     cfg.setGlobalJobTags("JobName, custom_tag:$ENV_VAR");

    //     Run run = new BuildStub(job, Result.SUCCESS, envVars, null, 10L, 2, null, 0L, null);
    //     TaskListener listener = mock(TaskListener.class);

    //     BuildData bd = new BuildData(run, listener);
    //     DatadogEvent event = new BuildFinishedEventImpl(bd);

    //     System.out.println(String.format("tags: %s", event.getTags()));
    //     Assert.assertTrue(event.getTags().get("custom_tag").contains("value"));

    // }

}
