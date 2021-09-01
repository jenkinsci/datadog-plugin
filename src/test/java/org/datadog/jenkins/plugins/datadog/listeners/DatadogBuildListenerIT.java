package org.datadog.jenkins.plugins.datadog.listeners;

import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
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
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.transport.FakeTracesHttpClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DatadogBuildListenerIT extends DatadogTraceAbstractTest {

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

        final FakeTracesHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(1);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        double queueTime = buildSpan.getMetrics().get(CITags.QUEUE_TIME);
        assertTrue(queueTime > 0L);
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
        final FilePath ws = jenkins.getWorkspaceFor(project);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());
        InputStream gitZip = getClass().getClassLoader().getResourceAsStream("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip");
        if(gitZip != null) {
            ws.unzipFrom(gitZip);
        }
        FreeStyleBuild run = project.scheduleBuild2(0).get();
        final String buildPrefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();

        final FakeTracesHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(1);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertGitVariables(buildSpan, "master");
        final Map<String, String> meta = buildSpan.getMeta();
        final Map<String, Double> metrics = buildSpan.getMetrics();
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), meta.get(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), meta.get(CITags._DD_CI_LEVEL));
        assertEquals(ORIGIN_CIAPP_PIPELINE, meta.get(CITags._DD_ORIGIN));
        assertEquals("jenkins.build", buildSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, buildSpan.getServiceName());
        assertEquals("buildIntegrationSuccess", buildSpan.getResourceName());
        assertEquals("ci", buildSpan.getType());
        assertEquals("jenkins", meta.get(CITags.CI_PROVIDER_NAME));
        assertEquals("anonymous", meta.get(CITags.USER_NAME));
        assertEquals("jenkins-buildIntegrationSuccess-1", meta.get(buildPrefix + CITags._ID));
        assertNotNull(metrics.get(CITags.QUEUE_TIME));
        assertEquals("buildIntegrationSuccess", meta.get(buildPrefix + CITags._NAME));
        assertEquals("1", meta.get(buildPrefix + CITags._NUMBER));
        assertNotNull(meta.get(buildPrefix + CITags._URL));
        assertNotNull(meta.get(CITags.WORKSPACE_PATH));
        assertEquals("success", meta.get(buildPrefix + CITags._RESULT));
        assertEquals("success", meta.get(CITags.STATUS));
        assertNotNull(meta.get(CITags.NODE_NAME));
        assertNotNull(meta.get(CITags.NODE_LABELS));
        assertNull(meta.get(CITags._DD_HOSTNAME));
        assertEquals("success", meta.get(CITags.JENKINS_RESULT));
        assertEquals("jenkins-buildIntegrationSuccess-1", meta.get(CITags.JENKINS_TAG));
        assertNotNull(meta.get(CITags._DD_CI_STAGES));
        assertEquals("[]", meta.get(CITags._DD_CI_STAGES));

        assertCleanupActions(run);
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

        final FakeTracesHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(1);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertGitVariables(buildSpan, "hardcoded-master");
    }

    @Test
    public void testGitAlternativeRepoUrl() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL_1", "https://github.com/johndoe/foobar.git");
        jenkins.getGlobalNodeProperties().add(prop);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessAltRepoUrl");
        final FilePath ws = jenkins.getWorkspaceFor(project);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());
        InputStream gitZip = getClass().getClassLoader().getResourceAsStream("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip");
        if(gitZip != null) {
            ws.unzipFrom(gitZip);
        }

        project.scheduleBuild2(0).get();

        final FakeTracesHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(1);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertGitVariables(buildSpan, "master");
    }

    @Test
    public void testTracesDisabled() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(false);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccess-notraces");
        project.scheduleBuild2(0).get();

        final FakeTracesHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(0);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(0, spans.size());
    }

    @Test
    public void testCITagsOnTraces() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setGlobalJobTags("(.*?)_job, global_job_tag:$ENV_VAR");
        cfg.setGlobalTags("global_tag:$ENV_VAR");
        EnvVars.masterEnvVars.put("ENV_VAR", "value");

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessTags_job");
        project.scheduleBuild2(0).get();

        final FakeTracesHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(1);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> meta = buildSpan.getMeta();
        assertEquals("value", meta.get("global_job_tag"));
        assertEquals("value", meta.get("global_tag"));
    }

    @Test
    public void testAvoidSettingEmptyGitInfoOnTraces() throws Exception {
        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessTagsNoGitInfo");
        project.scheduleBuild2(0).get();

        final FakeTracesHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(1);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> meta = buildSpan.getMeta();
        assertNull(meta.get(CITags.GIT_REPOSITORY_URL));
        assertNull(meta.get(CITags.GIT_BRANCH));
        assertNull(meta.get(CITags.GIT_DEFAULT_BRANCH));
        assertNull(meta.get(CITags.GIT_COMMIT_SHA));
        assertNull(meta.get(CITags.GIT_COMMIT_AUTHOR_NAME));
        assertNull(meta.get(CITags.GIT_COMMIT_AUTHOR_EMAIL));
        assertNull(meta.get(CITags.GIT_COMMIT_AUTHOR_DATE));
        assertNull(meta.get(CITags.GIT_COMMIT_COMMITTER_NAME));
        assertNull(meta.get(CITags.GIT_COMMIT_COMMITTER_EMAIL));
        assertNull(meta.get(CITags.GIT_COMMIT_COMMITTER_DATE));
    }
}
