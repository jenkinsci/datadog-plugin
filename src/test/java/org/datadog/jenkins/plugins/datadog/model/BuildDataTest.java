package org.datadog.jenkins.plugins.datadog.model;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.stubs.BuildStub;
import org.datadog.jenkins.plugins.datadog.stubs.ProjectStub;
import org.junit.Test;

public class BuildDataTest {

    @Test
    public void testBaseJobNameIsTakenFromParentName() throws IOException, InterruptedException {
        Run run = givenJobRun("jobName", "jobParentName", Collections.singletonMap("JOB_BASE_NAME", "jobBaseNameFromEnvironment"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jobParentName", buildData.getBaseJobName(""));
    }

    @Test
    public void testBaseJobNameFallsBackToEnvVar() throws IOException, InterruptedException {
        Run run = givenJobRun("jobName", "", Collections.singletonMap("JOB_BASE_NAME", "jobBaseNameFromEnvironment"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jobBaseNameFromEnvironment", buildData.getBaseJobName(""));
    }

    @Test
    public void testBaseJobNameFallsBackToJobName() throws IOException, InterruptedException {
        Run run = givenJobRun("jobName", "", Collections.singletonMap("JOB_NAME", "jobNameFromEnvironment"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jobName", buildData.getBaseJobName(""));
    }

    @Test
    public void testBaseJobNameFallsBackToJobNameFromEnvVar() throws IOException, InterruptedException {
        Run run = givenJobRun("", "", Collections.singletonMap("JOB_NAME", "jobNameFromEnvironment"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jobNameFromEnvironment", buildData.getBaseJobName(""));
    }

    @Test
    public void testJobNameIsTakenFromFullJobName() throws IOException, InterruptedException {
        Run run = givenJobRun("jobName", "jobParentName", Collections.singletonMap("JOB_BASE_NAME", "jobBaseNameFromEnvironment"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jobParentName/jobName", buildData.getJobName(""));
    }

    @Test
    public void testJobNameFallsBackToEnvVar() throws IOException, InterruptedException {
        Run run = givenJobRun("", "", Collections.singletonMap("JOB_NAME", "jobNameFromEnvironment"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jobNameFromEnvironment", buildData.getJobName(""));
    }

    @Test
    public void testBuildTagIsTakenFromEnvVar() throws IOException, InterruptedException {
        Run run = givenJobRun("jobName", "jobParentName", Collections.singletonMap("BUILD_TAG", "buildTag"));
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("buildTag", buildData.getBuildTag(""));
    }

    @Test
    public void testBuildTagFallsBackToAlternativeEnvVars() throws IOException, InterruptedException {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("JOB_NAME", "jobName");
        envVars.put("BUILD_NUMBER", "buildNumber");
        Run run = givenJobRun("jobName", "jobParentName", envVars);
        BuildData buildData = whenCreatingBuildData(run);
        assertEquals("jenkins-jobName-buildNumber", buildData.getBuildTag(""));
    }

    private Run givenJobRun(String jobName, String jobParentName, Map<String, String> environment) throws IOException {
        Jenkins jenkins = mock(Hudson.class);
        when(jenkins.getFullName()).thenReturn(jobParentName);
        ProjectStub job = new ProjectStub(jenkins, jobName);

        EnvVars envVars = new EnvVars();
        envVars.putAll(environment);

        return new BuildStub(job, null, envVars, null, 10L, 2, null, 0L, null);
    }

    private BuildData whenCreatingBuildData(Run run) throws IOException, InterruptedException {
        TaskListener listener = mock(TaskListener.class);
        BuildData bd = new BuildData(run, listener);
        return bd;
    }


}
