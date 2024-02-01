package org.datadog.jenkins.plugins.datadog.model;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class BuildDataTest {

    @Test
    public void testJobNameIsTakenFromRunParent() throws Exception {
        Run run = givenJobRun("jobName", "jobParentName", mock(Hudson.class), Collections.singletonMap("JOB_NAME", "jobNameFromEnvironment"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jobName", buildData.getJobName());
    }

    @Test
    public void testJobNameFallsBackToEnvVar() throws Exception {
        Run run = givenJobRun("", "", mock(Hudson.class), Collections.singletonMap("JOB_NAME", "jobNameFromEnvironment"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jobNameFromEnvironment", buildData.getJobName());
    }

    @Test
    public void testJobNameIsTakenFromJobParentForMultibranchBuilds() throws Exception {
        WorkflowMultiBranchProject jobParent = mock(WorkflowMultiBranchProject.class);
        when(jobParent.getFullName()).thenReturn("multibranch-project-name");

        WorkflowJob job = mock(WorkflowJob.class);
        when(job.getFullName()).thenReturn("multibranch-project-name/PR-1234");
        when(job.getParent()).thenAnswer((Answer<?>) (Answer<Object>) invocationOnMock -> jobParent);

        EnvVars envVars = new EnvVars();
        envVars.putAll(Collections.singletonMap("JOB_NAME", "jobNameFromEnvironment"));

        WorkflowRun run = mock(WorkflowRun.class);
        when(run.getEnvironment(any())).thenReturn(envVars);
        when(run.getParent()).thenReturn(job);
        when(run.getCharset()).thenReturn(StandardCharsets.UTF_8);

        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("multibranch-project-name", buildData.getJobName());
    }

    @Test
    public void testJobNameIsTakenFromJobParentForMatrixProjectBuilds() throws Exception {
        MatrixProject jobParent = mock(MatrixProject.class);
        when(jobParent.getFullName()).thenReturn("matrix-project-name");

        MatrixConfiguration job = mock(MatrixConfiguration.class);
        when(job.getFullName()).thenReturn("matrix-project-name/config1=value1,config2=value2");
        when(job.getParent()).thenReturn(jobParent);

        EnvVars envVars = new EnvVars();
        envVars.putAll(Collections.singletonMap("JOB_NAME", "jobNameFromEnvironment"));

        Run run = mock(Run.class);
        when(run.getEnvironment(any())).thenReturn(envVars);
        when(run.getParent()).thenReturn(job);
        when(run.getCharset()).thenReturn(StandardCharsets.UTF_8);

        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("matrix-project-name", buildData.getJobName());
    }

    @Test
    public void testBuildTagIsTakenFromEnvVar() throws Exception {
        Run run = givenJobRun("jobName", "jobParentName", mock(Hudson.class), Collections.singletonMap("BUILD_TAG", "buildTag"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("buildTag", buildData.getBuildTag(""));
    }

    @Test
    public void testBuildTagFallsBackToAlternativeEnvVars() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("JOB_NAME", "jobName");
        envVars.put("BUILD_NUMBER", "buildNumber");
        Run run = givenJobRun("jobName", "jobParentName", mock(Hudson.class), envVars);
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jenkins-jobName-buildNumber", buildData.getBuildTag(""));
    }

    private Run<?, ?> givenJobRun(String jobName, String jobParentName, ItemGroup<?> jobParent, Map<String, String> environment) throws Exception {
        when(jobParent.getFullName()).thenReturn(jobParentName);

        Job job = mock(Job.class);
        when(job.getFullName()).thenReturn(jobName);
        when(job.getParent()).thenReturn(jobParent);

        EnvVars envVars = new EnvVars();
        envVars.putAll(environment);

        Run run = mock(Run.class);
        when(run.getEnvironment(any())).thenReturn(envVars);
        when(run.getParent()).thenReturn(job);
        when(run.getCharset()).thenReturn(StandardCharsets.UTF_8);

        return run;
    }

    private BuildData whenCreatingBuildData(Run run) throws IOException, InterruptedException {
        TaskListener listener = mock(TaskListener.class);
        return new BuildData(run, listener);
    }

}
