package org.datadog.jenkins.plugins.datadog.model;

import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_DATE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_DATE;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
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

    @Test
    public void testNodeHostnameIsUsedForFreestyleBuilds() throws Exception {
        String computerHostname = "computer-hostname";

        Computer computer = mock(Computer.class);
        when(computer.getHostName()).thenReturn(computerHostname);

        Node node = mock(Node.class);
        when(node.toComputer()).thenReturn(computer);

        FreeStyleBuild build = givenJobRun(FreeStyleBuild.class, "jobName", "jobParentName", mock(Hudson.class), Collections.emptyMap());
        when(build.getBuiltOn()).thenReturn(node);

        BuildData buildData = whenCreatingBuildData(build);
        assertEquals(computerHostname, buildData.getHostname(""));
    }

    private Run<?, ?> givenJobRun(String jobName, String jobParentName, ItemGroup<?> jobParent, Map<String, String> environment) throws Exception {
        return givenJobRun(Run.class, jobName, jobParentName, jobParent, environment);
    }

    private <T extends Run> T givenJobRun(Class<T> runClass, String jobName, String jobParentName, ItemGroup<?> jobParent, Map<String, String> environment) throws Exception {
        when(jobParent.getFullName()).thenReturn(jobParentName);

        Job job = mock(Job.class);
        when(job.getFullName()).thenReturn(jobName);
        when(job.getParent()).thenReturn(jobParent);

        EnvVars envVars = new EnvVars();
        envVars.putAll(environment);

        T run = mock(runClass);
        when(run.getEnvironment(any())).thenReturn(envVars);
        when(run.getParent()).thenReturn(job);
        when(run.getCharset()).thenReturn(StandardCharsets.UTF_8);

        return run;
    }

    private BuildData whenCreatingBuildData(Run run) throws IOException, InterruptedException {
        TaskListener listener = mock(TaskListener.class);
        return new BuildData(run, listener);
    }

    @Test
    public void testCommitAuthorDateIsPopulatedFromEnvVar() throws Exception {
        String validISO8601Date = "2024-08-14T12:06:04.529Z";
        Map<String, String> buildEnvironment = Collections.singletonMap(DD_GIT_COMMIT_AUTHOR_DATE, validISO8601Date);
        FreeStyleBuild build = givenJobRun(FreeStyleBuild.class, "jobName", "jobParentName", mock(Hudson.class), buildEnvironment);
        BuildData buildData = whenCreatingBuildData(build);
        assertEquals(validISO8601Date, buildData.getGitAuthorDate(""));
    }

    @Test
    public void testCommitCommitterDateIsPopulatedFromEnvVar() throws Exception {
        String validISO8601Date = "2024-08-14T12:06:04.529Z";
        Map<String, String> buildEnvironment = Collections.singletonMap(DD_GIT_COMMIT_COMMITTER_DATE, validISO8601Date);
        FreeStyleBuild build = givenJobRun(FreeStyleBuild.class, "jobName", "jobParentName", mock(Hudson.class), buildEnvironment);
        BuildData buildData = whenCreatingBuildData(build);
        assertEquals(validISO8601Date, buildData.getGitCommitterDate(""));
    }

    @Test
    public void testCommitAuthorDateIsNotPopulatedWithInvalidValues() throws Exception {
        String invalidISO8601Date = "12:06:04.529 14/08/2024";
        Map<String, String> buildEnvironment = Collections.singletonMap(DD_GIT_COMMIT_AUTHOR_DATE, invalidISO8601Date);
        FreeStyleBuild build = givenJobRun(FreeStyleBuild.class, "jobName", "jobParentName", mock(Hudson.class), buildEnvironment);
        BuildData buildData = whenCreatingBuildData(build);
        assertEquals("", buildData.getGitAuthorDate(""));
    }

    @Test
    public void testCommitCommitterDateIsNotPopulatedWithInvalidValues() throws Exception {
        String invalidISO8601Date = "12:06:04.529 14/08/2024";
        Map<String, String> buildEnvironment = Collections.singletonMap(DD_GIT_COMMIT_COMMITTER_DATE, invalidISO8601Date);
        FreeStyleBuild build = givenJobRun(FreeStyleBuild.class, "jobName", "jobParentName", mock(Hudson.class), buildEnvironment);
        BuildData buildData = whenCreatingBuildData(build);
        assertEquals("", buildData.getGitCommitterDate(""));
    }

}
