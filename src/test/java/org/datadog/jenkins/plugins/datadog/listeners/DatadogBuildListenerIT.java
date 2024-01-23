package org.datadog.jenkins.plugins.datadog.listeners;

import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_BRANCH;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_DATE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_EMAIL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_NAME;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_DATE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_EMAIL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_NAME;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_MESSAGE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_SHA;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_DEFAULT_BRANCH;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_REPOSITORY_URL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.GIT_BRANCH;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.GIT_COMMIT;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.GIT_REPOSITORY_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;

public class DatadogBuildListenerIT extends DatadogTraceAbstractTest {

    private static final String SAMPLE_SERVICE_NAME = "sampleServiceName";

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private DatadogClientStub clientStub;

    static {
        // to allow checkout from local git repositories - needed for some tests
        GitSCM.ALLOW_LOCAL_CHECKOUT = true;
    }

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

        DumbSlave worker = null;
        try {
            worker = jenkinsRule.createOnlineSlave(Label.get("testBuild"));
            clientStub.waitForTraces(1);
            final List<TraceSpan> spans = clientStub.getSpans();
            assertEquals(1, spans.size());

            final TraceSpan buildSpan = spans.get(0);
            double queueTime = buildSpan.getMetrics().get(CITags.QUEUE_TIME);
            assertTrue(queueTime > 0L);
            assertTrue(queueTime > TimeUnit.NANOSECONDS.toSeconds(buildSpan.getDurationNano()));
            assertTrue(buildSpan.getDurationNano() > 1L);
        } finally {
            if(worker != null) {
                jenkinsRule.disconnectSlave(worker);
            }
        }
    }

    @Test
    public void testTraces() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", "file:///tmp/git-repo/");
        jenkins.getGlobalNodeProperties().add(prop);

        createLocallyAvailableGitRepo(jenkins);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccess");

        GitSCM git = new GitSCM(GitSCM.createRepoList("file:///tmp/git-repo/", null), Collections.singletonList(new BranchSpec("*/master")), null, null, Collections.singletonList(new LocalBranch("master")));
        project.setScm(git);

        final FilePath ws = jenkins.getWorkspaceFor(project);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        FreeStyleBuild run = project.scheduleBuild2(0).get();
        final String buildPrefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();

        clientStub.waitForTraces(1);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertGitVariablesOnSpan(buildSpan, "master");
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
        checkHostNameTag(meta);
        assertEquals("success", meta.get(CITags.JENKINS_RESULT));
        assertEquals("jenkins-buildIntegrationSuccess-1", meta.get(CITags.JENKINS_TAG));
        assertNull(meta.get(CITags._DD_CI_STAGES)); // this is a freestyle project which has no stages

        assertCleanupActions(run);
    }

    private void createLocallyAvailableGitRepo(Jenkins jenkins) throws IOException, InterruptedException {
        try (InputStream gitZip = getClass().getClassLoader().getResourceAsStream("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip")) {
            FilePath gitRepoPath = jenkins.createPath("/tmp/git-repo");
            gitRepoPath.deleteRecursive();
            gitRepoPath.mkdirs();
            gitRepoPath.unzipFrom(gitZip);
        }
    }

    @Test
    public void testGitDefaultBranch() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", "file:///tmp/git-repo/");
        final String defaultBranch = "refs/heads/hardcoded-master";
        env.put("DD_GIT_DEFAULT_BRANCH", defaultBranch);
        jenkins.getGlobalNodeProperties().add(prop);

        createLocallyAvailableGitRepo(jenkins);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessDefaultBranch");

        GitSCM git = new GitSCM(GitSCM.createRepoList("file:///tmp/git-repo/", null), Collections.singletonList(new BranchSpec("*/master")), null, null, Collections.singletonList(new LocalBranch("master")));
        project.setScm(git);

        project.scheduleBuild2(0).get();

        clientStub.waitForTraces(1);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertGitVariablesOnSpan(buildSpan, "hardcoded-master");
    }

    @Test
    public void testUserSuppliedGitWithoutCommitInfo() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put(GIT_REPOSITORY_URL, "not-valid-repo");
        env.put(GIT_BRANCH, "not-valid-branch");
        env.put(GIT_COMMIT, "not-valid-commit");

        env.put(DD_GIT_REPOSITORY_URL, "file:///tmp/git-repo/");
        env.put(DD_GIT_BRANCH, "master");
        env.put(DD_GIT_COMMIT_SHA, "401d997a6eede777602669ccaec059755c98161f");
        final String defaultBranch = "refs/heads/hardcoded-master";
        env.put(DD_GIT_DEFAULT_BRANCH, defaultBranch);
        jenkins.getGlobalNodeProperties().add(prop);

        createLocallyAvailableGitRepo(jenkins);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessUserSuppliedGitWithoutCommitInfo");

        GitSCM git = new GitSCM(GitSCM.createRepoList("file:///tmp/git-repo/", null), Collections.singletonList(new BranchSpec("*/master")), null, null, Collections.singletonList(new LocalBranch("master")));
        project.setScm(git);

        project.scheduleBuild2(0).get();

        clientStub.waitForTraces(1);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertGitVariablesOnSpan(buildSpan, "hardcoded-master");
    }

    @Test
    public void testUserSuppliedGitWithCommitInfo() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put(GIT_REPOSITORY_URL, "not-valid-repo");
        env.put(GIT_BRANCH, "not-valid-branch");
        env.put(GIT_COMMIT, "not-valid-commit");
        env.put(DD_GIT_REPOSITORY_URL, "file:///tmp/git-repo/");
        env.put(DD_GIT_BRANCH, "master");
        env.put(DD_GIT_COMMIT_SHA, "401d997a6eede777602669ccaec059755c98161f");
        env.put(DD_GIT_COMMIT_MESSAGE, "hardcoded-message");
        env.put(DD_GIT_COMMIT_AUTHOR_NAME, "hardcoded-author-name");
        env.put(DD_GIT_COMMIT_AUTHOR_EMAIL, "hardcoded-author-email");
        env.put(DD_GIT_COMMIT_AUTHOR_DATE, "hardcoded-author-date");
        env.put(DD_GIT_COMMIT_COMMITTER_NAME, "hardcoded-committer-name");
        env.put(DD_GIT_COMMIT_COMMITTER_EMAIL, "hardcoded-committer-email");
        env.put(DD_GIT_COMMIT_COMMITTER_DATE, "hardcoded-committer-date");
        final String defaultBranch = "refs/heads/hardcoded-master";
        env.put(DD_GIT_DEFAULT_BRANCH, defaultBranch);
        jenkins.getGlobalNodeProperties().add(prop);

        createLocallyAvailableGitRepo(jenkins);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessUserSuppliedGitWithCommitInfo");

        GitSCM git = new GitSCM(GitSCM.createRepoList("file:///tmp/git-repo/", null), Collections.singletonList(new BranchSpec("*/master")), null, null, Collections.singletonList(new LocalBranch("master")));
        project.setScm(git);

        project.scheduleBuild2(0).get();

        clientStub.waitForTraces(1);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> meta = buildSpan.getMeta();
        assertEquals("hardcoded-message", meta.get(CITags.GIT_COMMIT_MESSAGE));
        assertEquals("hardcoded-author-name", meta.get(CITags.GIT_COMMIT_AUTHOR_NAME));
        assertEquals("hardcoded-author-email", meta.get(CITags.GIT_COMMIT_AUTHOR_EMAIL));
        assertEquals("hardcoded-author-date", meta.get(CITags.GIT_COMMIT_AUTHOR_DATE));
        assertEquals("hardcoded-committer-name", meta.get(CITags.GIT_COMMIT_COMMITTER_NAME));
        assertEquals("hardcoded-committer-email", meta.get(CITags.GIT_COMMIT_COMMITTER_EMAIL));
        assertEquals("hardcoded-committer-date", meta.get(CITags.GIT_COMMIT_COMMITTER_DATE));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT__SHA));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT_SHA));
        assertEquals("master", meta.get(CITags.GIT_BRANCH));
        assertEquals("file:///tmp/git-repo/", meta.get(CITags.GIT_REPOSITORY_URL));
        assertEquals("hardcoded-master", meta.get(CITags.GIT_DEFAULT_BRANCH));
    }

    @Test
    public void testUserSuppliedGitWithCommitInfoWebhook() throws Exception {
        clientStub.configureForWebhooks();

        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put(GIT_REPOSITORY_URL, "not-valid-repo");
        env.put(GIT_BRANCH, "not-valid-branch");
        env.put(GIT_COMMIT, "not-valid-commit");
        env.put(DD_GIT_REPOSITORY_URL, "file:///tmp/git-repo/");
        env.put(DD_GIT_BRANCH, "master");
        env.put(DD_GIT_COMMIT_SHA, "401d997a6eede777602669ccaec059755c98161f");
        env.put(DD_GIT_COMMIT_MESSAGE, "hardcoded-message");
        env.put(DD_GIT_COMMIT_AUTHOR_NAME, "hardcoded-author-name");
        env.put(DD_GIT_COMMIT_AUTHOR_EMAIL, "hardcoded-author-email");
        env.put(DD_GIT_COMMIT_AUTHOR_DATE, "hardcoded-author-date");
        env.put(DD_GIT_COMMIT_COMMITTER_NAME, "hardcoded-committer-name");
        env.put(DD_GIT_COMMIT_COMMITTER_EMAIL, "hardcoded-committer-email");
        env.put(DD_GIT_COMMIT_COMMITTER_DATE, "hardcoded-committer-date");
        final String defaultBranch = "refs/heads/hardcoded-master";
        env.put(DD_GIT_DEFAULT_BRANCH, defaultBranch);
        jenkins.getGlobalNodeProperties().add(prop);

        createLocallyAvailableGitRepo(jenkins);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessUserSuppliedGitWithCommitInfoWebhook");

        GitSCM git = new GitSCM(GitSCM.createRepoList("file:///tmp/git-repo/", null), Collections.singletonList(new BranchSpec("*/master")), null, null, Collections.singletonList(new LocalBranch("master")));
        project.setScm(git);

        project.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(1);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(1, webhooks.size());

        final JSONObject buildSpan = webhooks.get(0);
        final JSONObject meta = buildSpan.getJSONObject("git");
        assertEquals("hardcoded-message", meta.getString("message"));
        assertEquals("hardcoded-author-name", meta.getString("author_name"));
        assertEquals("hardcoded-author-email", meta.getString("author_email"));
        assertEquals("hardcoded-author-date", meta.getString("author_time"));
        assertEquals("hardcoded-committer-name", meta.getString("committer_name"));
        assertEquals("hardcoded-committer-email", meta.getString("committer_email"));
        assertEquals("hardcoded-committer-date", meta.getString("commit_time"));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.getString("sha"));
        assertEquals("master", meta.getString("branch"));
        assertEquals("file:///tmp/git-repo/", meta.getString("repository_url"));
        assertEquals("hardcoded-master", meta.getString("default_branch"));
    }

    @Test
    public void testRawRepositoryUrl() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put(GIT_REPOSITORY_URL, "not-valid-repo");
        env.put(GIT_BRANCH, "master");
        env.put(GIT_COMMIT, "401d997a6eede777602669ccaec059755c98161f");
        jenkins.getGlobalNodeProperties().add(prop);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessRawRepositoryUrl");

        project.scheduleBuild2(0).get();

        clientStub.waitForTraces(1);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> meta = buildSpan.getMeta();

        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT__SHA));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT_SHA));
        assertEquals("master", meta.get(CITags.GIT_BRANCH));
        assertEquals("not-valid-repo", meta.get(CITags.GIT_REPOSITORY_URL));
    }

    @Test
    public void testFilterSensitiveInfoRepoUrl() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put(GIT_REPOSITORY_URL, "http://user:pwd@hostname.com/repo.git");
        env.put(GIT_BRANCH, "master");
        env.put(GIT_COMMIT, "401d997a6eede777602669ccaec059755c98161f");
        jenkins.getGlobalNodeProperties().add(prop);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessFilterSensitiveInfoRepoUrl");

        project.scheduleBuild2(0).get();

        clientStub.waitForTraces(1);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> meta = buildSpan.getMeta();

        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT__SHA));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT_SHA));
        assertEquals("master", meta.get(CITags.GIT_BRANCH));
        assertEquals("http://hostname.com/repo.git", meta.get(CITags.GIT_REPOSITORY_URL));
    }

    @Test
    public void testGitAlternativeRepoUrlWebhook() throws Exception {
        clientStub.configureForWebhooks();

        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL_1", "file:///tmp/git-repo/");
        jenkins.getGlobalNodeProperties().add(prop);

        createLocallyAvailableGitRepo(jenkins);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessAltRepoUrlWebhook");

        GitSCM git = new GitSCM(GitSCM.createRepoList("file:///tmp/git-repo/", null), Collections.singletonList(new BranchSpec("*/master")), null, null, Collections.singletonList(new LocalBranch("master")));
        project.setScm(git);

        final FilePath ws = jenkins.getWorkspaceFor(project);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        project.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(1);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(1, webhooks.size());

        final JSONObject webhook = webhooks.get(0);
        assertGitVariablesOnWebhook(webhook, "master");
    }

    @Test
    public void testTracesDisabled() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(false);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccess-notraces");
        project.scheduleBuild2(0).get();

        clientStub.waitForTraces(0);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(0, spans.size());
    }

    @Test
    public void testTracesDisabledWebhooks() throws Exception {
        clientStub.configureForWebhooks();

        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(false);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessWebhooks-notraces");
        project.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(0);
        assertEquals(0, clientStub.getWebhooks().size());
    }

    @Test
    public void testCITagsOnTraces() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setGlobalJobTags("(.*?)_job, global_job_tag:$ENV_VAR");
        cfg.setGlobalTags("global_tag:$ENV_VAR");
        EnvVars.masterEnvVars.put("ENV_VAR", "value");

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessTags_job");
        project.scheduleBuild2(0).get();

        clientStub.waitForTraces(1);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> meta = buildSpan.getMeta();
        assertEquals("value", meta.get("global_job_tag"));
        assertEquals("value", meta.get("global_tag"));
    }

    @Test
    public void testCITagsOnWebhooks() throws Exception {
        clientStub.configureForWebhooks();

        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setGlobalJobTags("(.*?)_job, global_job_tag:$ENV_VAR");
        cfg.setGlobalTags("global_tag:$ENV_VAR");
        EnvVars.masterEnvVars.put("ENV_VAR", "value");

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessTagsWebhooks_job");
        project.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(1);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(1, webhooks.size());

        final JSONObject webhook = webhooks.get(0);
        final JSONArray tags = webhook.getJSONArray("tags");
        assertTrue(tags.contains("global_job_tag:value"));
        assertTrue(tags.contains("global_tag:value"));
    }

    @Test
    public void testAvoidSettingEmptyGitInfoOnTraces() throws Exception {
        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccessTagsNoGitInfo");
        project.scheduleBuild2(0).get();

        clientStub.waitForTraces(1);
        final List<TraceSpan> spans = clientStub.getSpans();
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
