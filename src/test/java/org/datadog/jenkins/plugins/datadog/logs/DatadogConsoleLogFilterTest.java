package org.datadog.jenkins.plugins.datadog.logs;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Run;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DatadogConsoleLogFilterTest {

    @ClassRule
    public static JenkinsRule JENKINS = new JenkinsRule();


    @Test
    public void testExcludedJobsAreNotDecorated() {
        String jobName = "my-job";
        givenJobIsExcludedFromTracking(jobName);
        WorkflowRun run = givenBuildNamed(jobName);

        OutputStream originalStream = new ByteArrayOutputStream();
        DatadogConsoleLogFilter filter = new DatadogConsoleLogFilter();
        OutputStream decoratedStream = filter.decorateLogger(run, originalStream);

        assertSame(originalStream, decoratedStream);
    }

    @Test
    public void testExcludedJobsProvidedViaConstructorAreNotDecorated() {
        String jobName = "my-job";
        givenJobIsExcludedFromTracking(jobName);
        WorkflowRun run = givenBuildNamed(jobName);

        OutputStream originalStream = new ByteArrayOutputStream();
        DatadogConsoleLogFilter filter = new DatadogConsoleLogFilter(run);
        OutputStream decoratedStream = filter.decorateLogger((Run) null, originalStream);

        assertSame(originalStream, decoratedStream);
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
