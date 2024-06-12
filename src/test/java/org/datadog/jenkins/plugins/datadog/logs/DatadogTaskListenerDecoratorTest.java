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
package org.datadog.jenkins.plugins.datadog.logs;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DatadogTaskListenerDecoratorTest {
    @ClassRule
    public static JenkinsRule JENKINS = new JenkinsRule();

    private WorkflowRun workflowRun;   
    private WorkflowJob job; 

    @Before
    public void setupMock() throws Exception {
        workflowRun = mock(WorkflowRun.class);
        job = mock(WorkflowJob.class);

        when(job.getFullName()).thenReturn("Pipeline job");
        when(workflowRun.getParent()).thenReturn(job);
        when(workflowRun.getEnvironment(any())).thenReturn(mock(EnvVars.class));
        when(workflowRun.getCharset()).thenReturn(mock(Charset.class));
    }

    @Test
    public void testSerializeDeserialize() throws Exception {
        DatadogTaskListenerDecorator datadogTaskListenerDecorator = new DatadogTaskListenerDecorator(workflowRun);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);

        datadogTaskListenerDecorator.decorate(System.out);

        // Serialize
        out.writeObject(datadogTaskListenerDecorator);
        out.flush();
        byte[] bytes = bos.toByteArray();
        out.close(); 
        bos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream in = new ObjectInputStream(bis);

        // Deserialize
        DatadogTaskListenerDecorator datadogTaskListenerDecoratorDes = (DatadogTaskListenerDecorator) in.readObject();
        in.close();
        bis.close();

        // Assert decorate can be called
        datadogTaskListenerDecoratorDes.decorate(System.out);
    }

    @Test
    public void testExcludedJobsAreNotDecorated() throws Exception {
        String jobName = "my-job";

        givenJobIsExcludedFromTracking(jobName);
        WorkflowRun run = givenBuildNamed(jobName);

        FlowExecutionOwner executionOwner = mock(FlowExecutionOwner.class);
        when(executionOwner.getExecutable()).thenReturn(run);
        DatadogTaskListenerDecorator.Factory factory = new DatadogTaskListenerDecorator.Factory();
        TaskListenerDecorator taskListenerDecorator = factory.of(executionOwner);

        assertNull(taskListenerDecorator);
    }

    private static void givenJobIsExcludedFromTracking(String jobName) {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setExcluded(jobName);
    }

    private static @NotNull WorkflowRun givenBuildNamed(String jobName) {
        WorkflowJob job = mock(WorkflowJob.class);
        when(job.getFullName()).thenReturn(jobName);

        WorkflowRun run = mock(WorkflowRun.class);
        when(run.getParent()).thenReturn(job);
        return run;
    }
}
