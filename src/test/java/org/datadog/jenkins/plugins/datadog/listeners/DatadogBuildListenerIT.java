package org.datadog.jenkins.plugins.datadog.listeners;

import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import datadog.trace.common.writer.ListWriter;
import datadog.trace.core.DDSpan;
import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DatadogBuildListenerIT {

    private static final String SAMPLE_SERVICE_NAME = "sampleServiceName";

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private DatadogClientStub clientStub;

    @Before
    public void beforeEach() throws IOException {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);
        cfg.setCiInstanceName(SAMPLE_SERVICE_NAME);
        cfg.setGlobalJobTags(null);
        cfg.setGlobalTags(null);
        EnvVars.masterEnvVars.remove("ENV_VAR");

        clientStub = new DatadogClientStub();
        ClientFactory.setTestClient(clientStub);
        clientStub.tracerWriter.start();

        Jenkins jenkins = jenkinsRule.jenkins;
        jenkins.getGlobalNodeProperties().remove(EnvironmentVariablesNodeProperty.class);
    }

    @Test
    public void testTracesQueueTime() throws Exception{
        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessQueue");
        project.setAssignedLabel(Label.get("testBuild"));
        new Thread(() -> {
            try {
                project.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(5000);
        jenkinsRule.createOnlineSlave(Label.get("testBuild"));

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(1);
        assertEquals(1, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final DDSpan buildSpan = buildTrace.get(0);
        long queueTime = (long) buildSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(queueTime > 0L);
        assertEquals("none", buildSpan.getTag(CITags._DD_HOSTNAME));
        assertTrue(queueTime > TimeUnit.NANOSECONDS.toSeconds(buildSpan.getDurationNano()));
        assertTrue(buildSpan.getDurationNano() > 1L);
    }

    @Test
    public void testTraces() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", "https://github.com/johndoe/foobar.git");
        jenkins.getGlobalNodeProperties().add(prop);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccess");
        final URL gitZip = getClass().getClassLoader().getResource("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip");
        if(gitZip != null) {
            project.setScm(new ExtractResourceSCM(gitZip));
        }
        project.scheduleBuild2(0).get();

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(1);
        assertEquals(1, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final String buildPrefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();
        final DDSpan buildSpan = buildTrace.get(0);
        assertGitVariables(buildSpan, "master");
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpan.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpan.getTag(CITags._DD_CI_LEVEL));
        assertEquals(ORIGIN_CIAPP_PIPELINE, buildSpan.getTag(CITags._DD_ORIGIN));
        assertEquals("jenkins.build", buildSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, buildSpan.getServiceName());
        assertEquals("buildIntegrationSuccess", buildSpan.getResourceName());
        assertEquals("ci", buildSpan.getType());
        assertEquals("jenkins", buildSpan.getTag(CITags.CI_PROVIDER_NAME));
        assertEquals("anonymous", buildSpan.getTag(CITags.USER_NAME));
        assertEquals("jenkins-buildIntegrationSuccess-1", buildSpan.getTag(buildPrefix + CITags._ID));
        assertNotNull(buildSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("buildIntegrationSuccess", buildSpan.getTag(buildPrefix + CITags._NAME));
        assertEquals("1", buildSpan.getTag(buildPrefix + CITags._NUMBER));
        assertNotNull(buildSpan.getTag(buildPrefix + CITags._URL));
        assertNotNull(buildSpan.getTag(CITags.WORKSPACE_PATH));
        assertEquals("success", buildSpan.getTag(buildPrefix + CITags._RESULT));
        assertEquals("success", buildSpan.getTag(CITags.STATUS));
        assertNotNull(buildSpan.getTag(CITags.NODE_NAME));
        assertNotNull(buildSpan.getTag(CITags.NODE_LABELS));
        assertNull(buildSpan.getTag(CITags._DD_HOSTNAME));
        assertEquals("success", buildSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins-buildIntegrationSuccess-1", buildSpan.getTag(CITags.JENKINS_TAG));
        assertNotNull(buildSpan.getTag(CITags._DD_CI_STAGES));
        assertEquals("[]", buildSpan.getTag(CITags._DD_CI_STAGES));
    }

    @Test
    public void testGitDefaultBranch() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", "https://github.com/johndoe/foobar.git");
        final String defaultBranch = "refs/heads/hardcoded-master";
        env.put("DD_GIT_DEFAULT_BRANCH", defaultBranch);
        jenkins.getGlobalNodeProperties().add(prop);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessDefaultBranch");
        final URL gitZip = getClass().getClassLoader().getResource("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip");
        if(gitZip != null) {
            project.setScm(new ExtractResourceSCM(gitZip));
        }
        project.scheduleBuild2(0).get();

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(1);
        assertEquals(1, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final DDSpan buildSpan = buildTrace.get(0);
        assertGitVariables(buildSpan, "hardcoded-master");
    }

    @Test
    public void testTracesDisabled() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(false);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccess-notraces");
        project.scheduleBuild2(0).get();

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(0);
        assertEquals(0, tracerWriter.size());
    }

    @Test
    public void testCITagsOnTraces() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setGlobalJobTags("(.*?)_job, global_job_tag:$ENV_VAR");
        cfg.setGlobalTags("global_tag:$ENV_VAR");
        EnvVars.masterEnvVars.put("ENV_VAR", "value");

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessTags_job");
        project.scheduleBuild2(0).get();

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(1);
        assertEquals(1, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("value", buildSpan.getTag("global_job_tag"));
        assertEquals("value", buildSpan.getTag("global_tag"));
    }


    private void assertGitVariables(DDSpan span, String defaultBranch) {
        assertEquals("Initial commit\n", span.getTag(CITags.GIT_COMMIT_MESSAGE));
        assertEquals("John Doe", span.getTag(CITags.GIT_COMMIT_AUTHOR_NAME));
        assertEquals("john@doe.com", span.getTag(CITags.GIT_COMMIT_AUTHOR_EMAIL));
        assertEquals("2020-10-08T07:49:32.000Z", span.getTag(CITags.GIT_COMMIT_AUTHOR_DATE));
        assertEquals("John Doe", span.getTag(CITags.GIT_COMMIT_COMMITTER_NAME));
        assertEquals("john@doe.com", span.getTag(CITags.GIT_COMMIT_COMMITTER_EMAIL));
        assertEquals("2020-10-08T07:49:32.000Z", span.getTag(CITags.GIT_COMMIT_COMMITTER_DATE));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", span.getTag(CITags.GIT_COMMIT__SHA));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", span.getTag(CITags.GIT_COMMIT_SHA));
        assertEquals("master", span.getTag(CITags.GIT_BRANCH));
        assertEquals("https://github.com/johndoe/foobar.git", span.getTag(CITags.GIT_REPOSITORY_URL));
        assertEquals(defaultBranch, span.getTag(CITags.GIT_DEFAULT_BRANCH));
    }
}
