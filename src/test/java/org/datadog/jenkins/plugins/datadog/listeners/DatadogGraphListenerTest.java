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
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_TAG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Cause.UserIdCause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.triggers.TimerTrigger.TimerTriggerCause;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;
import org.datadog.jenkins.plugins.datadog.publishers.DatadogComputerPublisher;
import org.datadog.jenkins.plugins.datadog.publishers.DatadogCountersPublisher;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DatadogGraphListenerTest extends DatadogTraceAbstractTest {

    private static final String SAMPLE_SERVICE_NAME = "sampleServiceName";

    @ClassRule
    public static JenkinsRule jenkinsRule;

    private static FilePath localGitRepoPath;

    static {
        // to allow checkout from local git repositories - needed for some tests
        System.setProperty("hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT", "true");

        jenkinsRule = new JenkinsRule();
        jenkinsRule.timeout = 600; // default value of 180 is too small for all the test cases in this class
    }

    @BeforeClass
    public static void setUp() {
        localGitRepoPath = jenkinsRule.jenkins.getRootPath().child("tmp").child("git-repo");
        try (InputStream gitZip = DatadogBuildListenerIT.class.getClassLoader().getResourceAsStream("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip")) {
            localGitRepoPath.deleteRecursive();
            localGitRepoPath.mkdirs();
            localGitRepoPath.unzipFrom(gitZip);

        } catch (Exception e) {
            throw new RuntimeException("Could not create local git repo at " + localGitRepoPath.getRemote(), e);
        }
    }

    @AfterClass
    public static void tearDown() throws IOException, InterruptedException {
        localGitRepoPath.deleteRecursive();
    }

    private DatadogGraphListener listener;
    private DatadogClientStub clientStub;

    @Before
    public void beforeEach() throws IOException {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);
        cfg.setCiInstanceName(SAMPLE_SERVICE_NAME);
        cfg.setGlobalJobTags(null);
        cfg.setGlobalTags(null);
        EnvVars.masterEnvVars.remove("ENV_VAR");

        listener = new DatadogGraphListener();
        clientStub = new DatadogClientStub();
        ClientHolder.setClient(clientStub);

        Jenkins jenkins = jenkinsRule.jenkins;
        jenkins.getGlobalNodeProperties().remove(EnvironmentVariablesNodeProperty.class);
    }

    private StepStartNode makeMonitorableStartNode(String label) {
        StepStartNode startNode = mock(StepStartNode.class);
        when(startNode.getAction(LabelAction.class)).thenReturn(new LabelAction(label));
        when(startNode.getDisplayName()).thenReturn(label);
        return startNode;
    }

    @Test
    public void testNewNode() throws IOException {
        StepStartNode startNode = makeMonitorableStartNode("low");
        StepEndNode endNode = mock(StepEndNode.class);
        when(endNode.getStartNode()).thenReturn(startNode);

        long startTime = 10L, endTime = 12345L;

        TimingAction startAction = mock(TimingAction.class);
        when(startAction.getStartTime()).thenReturn(startTime);
        TimingAction endAction = mock(TimingAction.class);
        when(endAction.getStartTime()).thenReturn(endTime);

        when(startNode.getAction(TimingAction.class)).thenReturn(startAction);
        when(endNode.getAction(TimingAction.class)).thenReturn(endAction);

        List<BlockStartNode> enclosingBlocks = new ArrayList<>();
        enclosingBlocks.add(makeMonitorableStartNode("medium"));
        enclosingBlocks.add(makeMonitorableStartNode("high"));
        when(startNode.iterateEnclosingBlocks()).thenReturn(enclosingBlocks);

        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipeline");
        WorkflowRun run = new WorkflowRun(job);

        FlowExecutionOwner flowExecutionOwner = mock(FlowExecutionOwner.class);
        when(flowExecutionOwner.getExecutable()).thenReturn(run);
        FlowExecution flowExecution = mock(FlowExecution.class);
        when(flowExecution.getOwner()).thenReturn(flowExecutionOwner);
        when(endNode.getExecution()).thenReturn(flowExecution);

        listener.onNewHead(endNode);

        DatadogCountersPublisher.publishMetrics(clientStub);

        String hostname = DatadogUtilities.getHostname(null);
        String[] expectedTags = new String[] { "jenkins_url:" + DatadogUtilities.getJenkinsUrl(), "user_id:anonymous",
                "stage_name:low", "job:pipeline", "parent_stage_name:medium", "stage_depth:2", "result:SUCCESS" };
        clientStub.assertMetric("jenkins.job.stage_duration", endTime - startTime, hostname, expectedTags);
        clientStub.assertMetric("jenkins.job.stage_pause_duration", 0, hostname, expectedTags);
        clientStub.assertMetric("jenkins.job.stage_completed", 1, hostname, expectedTags);
    }

    @Test
    public void testIntegration() throws Exception {
        EnvVars windowsEnvVars = new EnvVars();
        String windowsHostname = "windows-hostname";
        windowsEnvVars.put("HOSTNAME", windowsHostname);
        jenkinsRule.createOnlineSlave(new LabelAtom("windows"), windowsEnvVars);
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration");
        String definition = getPipelineDefinition("testPipelineDefinition.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        WorkflowRun run = job.scheduleBuild2(0).get();
        BufferedReader br = new BufferedReader(run.getLogReader());
        String s;
        while ((s = br.readLine()) != null) {
            System.out.println(s);
        }
        br.close();
        String[] baseTags = new String[]{
                "jenkins_url:" + DatadogUtilities.getJenkinsUrl(),
                "user_id:anonymous",
                "job:pipelineIntegration",
                "result:SUCCESS"
        };
        String[] depths = new String[]{ "2", "2", "2", "1", "1", "0", "0" };
        String[] stageNames = new String[]{ "Windows-1", "Windows-2", "Windows-3", "Test On Windows", "Test On Linux", "Parallel tests",
                "Pre-setup" };
        String[] parentNames = new String[]{ "Test On Windows", "Test On Windows", "Test On Windows", "Parallel tests", "Parallel tests", "root", "root" };

        for (int i = 0; i < depths.length; i++) {
            String hostname;
            String stageName = stageNames[i];
            if (stageName.contains("Windows")) {
                // agent { label "windows" }
                hostname = windowsHostname;
            } else {
                // agent { label "built-in" }
                hostname = DatadogUtilities.getHostname(null);
            }

            String[] expectedTags = Arrays.copyOf(baseTags, baseTags.length + 3);
            expectedTags[expectedTags.length - 3] = "stage_depth:" + depths[i];
            expectedTags[expectedTags.length - 2] = "stage_name:" + stageNames[i];
            expectedTags[expectedTags.length - 1] = "parent_stage_name:" + parentNames[i];
            clientStub.assertMetric("jenkins.job.stage_duration", hostname, expectedTags);

            if (stageName.equals("Test On Linux") || stageName.equals("Parallel tests")) {
                // Timeout is set to 11s, but since there are other instructions,
                // we test it's at least 10s.
                double pauseValue = clientStub.assertMetricGetValue("jenkins.job.stage_pause_duration", hostname, expectedTags);
                assertTrue(pauseValue > 10000);
                assertTrue(pauseValue <= 11100);
            } else {
                clientStub.assertMetric("jenkins.job.stage_pause_duration", 0.0, hostname, expectedTags);
            }
        }

        clientStub.waitForTraces(16);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(16, spans.size());
    }

    @Test
    public void testIntegrationGitInfo() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", toUrl(localGitRepoPath.getRemote()));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationSingleCommit");
        String definition = getPipelineDefinition("testPipelineSuccessLocalCheckout.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        createLocallyAvailableGitRepo(jenkins);

        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        final TraceSpan buildSpan = spans.get(0);
        assertGitVariablesOnSpan(buildSpan, "master", toUrl(localGitRepoPath.getRemote()));
    }

    @NotNull
    private static String toUrl(String path) {
        if (isRunningOnWindows()) {
            return "file:///" + path.replace('\\', '/');
        } else {
            return "file://" + path;
        }
    }

    @Test
    public void testIntegrationNonCIVisibilityEnvVars() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "testPipelineGitBranchEnv");
        String definition = getPipelineDefinition("testPipelineGitBranchEnv.txt");

        String[] expectedTags = new String[]{
                "jenkins_url:" + DatadogUtilities.getJenkinsUrl(),
                "user_id:anonymous",
                "job:testPipelineGitBranchEnv",
                "branch:test-branch",
                "result:SUCCESS"
        };
        String hostname = DatadogUtilities.getHostname(null);
        job.setDefinition(new CpsFlowDefinition(definition, true));
        WorkflowRun run = job.scheduleBuild2(0).get();
        clientStub.assertMetric("jenkins.job.duration", hostname, expectedTags);

    }

    @Test
    public void testIntegrationGitInfoWebhooks() throws Exception {
        clientStub.configureForWebhooks();

        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", toUrl(localGitRepoPath.getRemote()));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationSingleCommitWebhooks");
        String definition = getPipelineDefinition("testPipelineSuccessLocalCheckout.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        createLocallyAvailableGitRepo(jenkins);

        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(3);
        final List<JSONObject> webhook = clientStub.getWebhooks();
        assertEquals(3, webhook.size());
        assertGitVariablesOnWebhook(webhook.get(0), "master", toUrl(localGitRepoPath.getRemote()));
    }

    @Test
    public void testIntegrationGitInfoDefaultBranchEnvVar() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", toUrl(localGitRepoPath.getRemote()));
        final String defaultBranch = "refs/heads/hardcoded-master";
        env.put("DD_GIT_DEFAULT_BRANCH", defaultBranch);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationSingleCommitDefaultBranchEnvVar");
        String definition = getPipelineDefinition("testPipelineSuccessLocalCheckout.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        createLocallyAvailableGitRepo(jenkins);

        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        final TraceSpan buildSpan = spans.get(0);
        assertGitVariablesOnSpan(buildSpan, "hardcoded-master", toUrl(localGitRepoPath.getRemote()));
    }

    @Test
    public void testIntegrationGitInfoOverrideCommit() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", toUrl(localGitRepoPath.getRemote()));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationOverrideCommit");
        String definition = getPipelineDefinition("testPipelinesOverrideGitCommit.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        createLocallyAvailableGitRepo(jenkins);

        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(5);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(5, spans.size());
        for(TraceSpan span : spans) {
            assertEquals("401d997a6eede777602669ccaec059755c98161f", span.getMeta().get(CITags.GIT_COMMIT_SHA));
        }
    }

    @Test
    public void testManuallySetCommitterDateIsExtractedFromEnvironmentVariables() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "testPipelinesManualGitCommitterDate.txt");
        String definition = getPipelineDefinition("testPipelinesManualGitCommitterDate.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);

        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        for(TraceSpan span : spans) {
            if ("jenkins.build".equals(span.getOperationName())) {
                assertEquals("2024-08-14T12:06:04.529Z", span.getMeta().get(CITags.GIT_COMMIT_COMMITTER_DATE));
            }
        }
    }

    @Test
    public void testManuallySetAuthorDateIsExtractedFromEnvironmentVariables() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "testPipelinesManualGitAuthorDate.txt");
        String definition = getPipelineDefinition("testPipelinesManualGitAuthorDate.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);

        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        for(TraceSpan span : spans) {
            if ("jenkins.build".equals(span.getOperationName())) {
                assertEquals("2024-08-14T12:06:04.529Z", span.getMeta().get(CITags.GIT_COMMIT_AUTHOR_DATE));
            }
        }
    }

    @Test
    public void testInvalidManuallySetCommitterDateIsExtractedFromEnvironmentVariables() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "testPipelinesInvalidManualGitCommitterDate.txt");
        String definition = getPipelineDefinition("testPipelinesInvalidManualGitCommitterDate.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);

        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        for(TraceSpan span : spans) {
            assertNull(span.getMeta().get(CITags.GIT_COMMIT_COMMITTER_DATE));
        }
    }

    @Test
    public void testInvalidManuallySetAuthorDateIsExtractedFromEnvironmentVariables() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "testPipelinesInvalidManualGitAuthorDate.txt");
        String definition = getPipelineDefinition("testPipelinesInvalidManualGitAuthorDate.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);

        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        for(TraceSpan span : spans) {
            assertNull(span.getMeta().get(CITags.GIT_COMMIT_AUTHOR_DATE));
        }
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$([A-Z_]+)");

    private String getPipelineDefinition(String file) throws IOException {
        Map<String, String> replacements = Collections.singletonMap("LOCAL_REPO_URL", toUrl(localGitRepoPath.getRemote()));

        String pipelineDefinition;
        try (InputStream is = DatadogGraphListenerTest.class.getResourceAsStream(file)) {
            StringBuffer pipelineBuilder = new StringBuffer();
            String pipelineTemplate = IOUtils.toString(is, StandardCharsets.UTF_8);
            Matcher m = PLACEHOLDER_PATTERN.matcher(pipelineTemplate);
            while (m.find()) {
                String placeholder = m.group(1);
                m.appendReplacement(pipelineBuilder, replacements.get(placeholder));
            }
            m.appendTail(pipelineBuilder);
            pipelineDefinition = pipelineBuilder.toString();
        }

        return pipelineDefinition;
    }

    @Test
    public void testIntegrationGitAlternativeRepoUrl() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL_1", toUrl(localGitRepoPath.getRemote()));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationAltRepoUrl");
        String definition = getPipelineDefinition("testPipelinesOverrideGitCommit.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        createLocallyAvailableGitRepo(jenkins);

        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(5);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(5, spans.size());
        for(TraceSpan span : spans) {
            assertEquals(toUrl(localGitRepoPath.getRemote()), span.getMeta().get(CITags.GIT_REPOSITORY_URL));
        }
    }

    @Test
    public void testIntegrationGitAlternativeRepoUrlWebhooks() throws Exception {
        clientStub.configureForWebhooks();

        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL_1", toUrl(localGitRepoPath.getRemote()));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationAltRepoUrlWebhooks");
        String definition = getPipelineDefinition("testPipelinesOverrideGitCommit.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        createLocallyAvailableGitRepo(jenkins);

        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(5);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(5, webhooks.size());
        for(JSONObject webhook : webhooks) {
            assertEquals(toUrl(localGitRepoPath.getRemote()), webhook.getJSONObject("git").get("repository_url"));
        }
    }

    @Test
    public void testUserSuppliedGitWithoutCommitInfo() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put(DD_GIT_REPOSITORY_URL, toUrl(localGitRepoPath.getRemote()));
        env.put(DD_GIT_BRANCH, "master");
        env.put(DD_GIT_COMMIT_SHA, "401d997a6eede777602669ccaec059755c98161f");
        env.put(DD_GIT_TAG, "0.1.0");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationUserSuppliedGitWithoutCommitInfo");
        String definition = getPipelineDefinition("testPipelineSuccessLocalCheckout.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        createLocallyAvailableGitRepo(jenkins);

        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        final TraceSpan buildSpan = spans.get(0);
        assertGitVariablesOnSpan(buildSpan, "master", toUrl(localGitRepoPath.getRemote()));
        final Map<String, String> meta = buildSpan.getMeta();
        assertEquals("0.1.0", meta.get(CITags.GIT_TAG));
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
    public void testUserSuppliedGitWithoutCommitInfoWebhooks() throws Exception {
        clientStub.configureForWebhooks();

        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put(DD_GIT_REPOSITORY_URL, toUrl(localGitRepoPath.getRemote()));
        env.put(DD_GIT_BRANCH, "master");
        env.put(DD_GIT_COMMIT_SHA, "401d997a6eede777602669ccaec059755c98161f");
        env.put(DD_GIT_TAG, "0.1.0");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationUserSuppliedGitWithoutCommitInfoWebhooks");
        String definition = getPipelineDefinition("testPipelineSuccessLocalCheckout.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        createLocallyAvailableGitRepo(jenkins);

        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(3);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(3, webhooks.size());
        final JSONObject webhook = webhooks.get(0);
        assertGitVariablesOnWebhook(webhook, "master", toUrl(localGitRepoPath.getRemote()));
        assertEquals("0.1.0", webhook.getJSONObject("git").get("tag"));
    }

    @Test
    public void testUserSuppliedGitWithCommitInfo() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put(DD_GIT_REPOSITORY_URL, toUrl(localGitRepoPath.getRemote()));
        env.put(DD_GIT_BRANCH, "master");
        env.put(DD_GIT_COMMIT_SHA, "401d997a6eede777602669ccaec059755c98161f");
        env.put(DD_GIT_COMMIT_MESSAGE, "hardcoded-message");
        env.put(DD_GIT_COMMIT_AUTHOR_NAME, "hardcoded-author-name");
        env.put(DD_GIT_COMMIT_AUTHOR_EMAIL, "hardcoded-author-email");
        env.put(DD_GIT_COMMIT_AUTHOR_DATE, "2024-08-14T12:06:04.530Z");
        env.put(DD_GIT_COMMIT_COMMITTER_NAME, "hardcoded-committer-name");
        env.put(DD_GIT_COMMIT_COMMITTER_EMAIL, "hardcoded-committer-email");
        env.put(DD_GIT_COMMIT_COMMITTER_DATE, "2024-08-14T12:06:04.529Z");
        final String defaultBranch = "refs/heads/hardcoded-master";
        env.put(DD_GIT_DEFAULT_BRANCH, defaultBranch);
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationUserSuppliedGitWithCommitInfo");
        String definition = getPipelineDefinition("testPipelineSuccessLocalCheckout.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());

        createLocallyAvailableGitRepo(jenkins);

        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> meta = buildSpan.getMeta();
        assertEquals("hardcoded-message", meta.get(CITags.GIT_COMMIT_MESSAGE));
        assertEquals("hardcoded-author-name", meta.get(CITags.GIT_COMMIT_AUTHOR_NAME));
        assertEquals("hardcoded-author-email", meta.get(CITags.GIT_COMMIT_AUTHOR_EMAIL));
        assertEquals("2024-08-14T12:06:04.530Z", meta.get(CITags.GIT_COMMIT_AUTHOR_DATE));
        assertEquals("hardcoded-committer-name", meta.get(CITags.GIT_COMMIT_COMMITTER_NAME));
        assertEquals("hardcoded-committer-email", meta.get(CITags.GIT_COMMIT_COMMITTER_EMAIL));
        assertEquals("2024-08-14T12:06:04.529Z", meta.get(CITags.GIT_COMMIT_COMMITTER_DATE));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT__SHA));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT_SHA));
        assertEquals("master", meta.get(CITags.GIT_BRANCH));
        assertEquals(toUrl(localGitRepoPath.getRemote()), meta.get(CITags.GIT_REPOSITORY_URL));
        assertEquals("hardcoded-master", meta.get(CITags.GIT_DEFAULT_BRANCH));
    }

    @Test
    public void testRawRepositoryUrl() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put(DD_GIT_REPOSITORY_URL, "not-valid-repo");
        env.put(DD_GIT_BRANCH, "master");
        env.put(DD_GIT_COMMIT_SHA, "401d997a6eede777602669ccaec059755c98161f");
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationRawRepositoryUrl");
        String definition = getPipelineDefinition("testPipelineSuccess.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
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
        env.put(DD_GIT_REPOSITORY_URL, "http://user:pwd@hostname.com/repo.git");
        env.put(DD_GIT_BRANCH, "master");
        env.put(DD_GIT_COMMIT_SHA, "401d997a6eede777602669ccaec059755c98161f");
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationFilterSensitiveInfoRepoUrl");
        String definition = getPipelineDefinition("testPipelineSuccess.txt");

        job.setDefinition(new CpsFlowDefinition(definition, true));
        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> meta = buildSpan.getMeta();
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT__SHA));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT_SHA));
        assertEquals("master", meta.get(CITags.GIT_BRANCH));
        assertEquals("http://hostname.com/repo.git", meta.get(CITags.GIT_REPOSITORY_URL));
    }

    @Test
    public void testStageNamePropagation() throws Exception{
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationStages");
        String definition = getPipelineDefinition("testPipelineStages.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        jenkinsRule.createOnlineSlave(Label.get("testStageName"));

        clientStub.waitForTraces(6);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(6, spans.size());

        final TraceSpan stage1 = searchSpan(spans, "Stage 1");
        final String stage1Name = stage1.getMeta().get(PipelineStepData.StepType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage1Name != null && !stage1Name.isEmpty());

        final TraceSpan stepStage1 = searchFirstChild(spans, stage1);
        assertEquals(stage1Name, stepStage1.getMeta().get(PipelineStepData.StepType.STAGE.getTagName() + CITags._NAME));

        final TraceSpan stage2 = searchSpan(spans, "Stage 2");
        final String stage2Name = stage2.getMeta().get(PipelineStepData.StepType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage2Name != null && !stage2Name.isEmpty());

        final TraceSpan stepStage2 = searchFirstChild(spans, stage2);
        assertEquals(stage2Name, stepStage2.getMeta().get(PipelineStepData.StepType.STAGE.getTagName() + CITags._NAME));
    }

    @Test
    public void testStageNamePropagationWebhook() throws Exception{
        clientStub.configureForWebhooks();

        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationStagesWebhook");
        String definition = getPipelineDefinition("testPipelineStagesWebhook.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        jenkinsRule.createOnlineSlave(Label.get("testStageNameWebhook"));

        clientStub.waitForWebhooks(6);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(6, webhooks.size());

        final JSONObject pipeline = searchWebhook(webhooks, "pipelineIntegrationStagesWebhook");
        assertEquals("pipeline", pipeline.getString("level"));

        final JSONObject stage1 = searchWebhook(webhooks, "Stage 1");
        assertEquals("stage", stage1.getString("level"));
        assertEquals("pipelineIntegrationStagesWebhook", stage1.getString("pipeline_name"));

        final JSONObject stepStage1 = searchFirstChild(webhooks, stage1);
        assertEquals("job", stepStage1.getString("level"));
        assertEquals("Stage 1", stepStage1.getString("stage_name"));
        assertEquals("pipelineIntegrationStagesWebhook", stepStage1.getString("pipeline_name"));

        final JSONObject stage2 = searchWebhook(webhooks, "Stage 2");
        assertEquals("stage", stage2.getString("level"));
        assertEquals("pipelineIntegrationStagesWebhook", stage2.getString("pipeline_name"));

        final JSONObject stepStage2 = searchFirstChild(webhooks, stage2);
        assertEquals("job", stepStage2.getString("level"));
        assertEquals("Stage 2", stepStage2.getString("stage_name"));
        assertEquals("pipelineIntegrationStagesWebhook", stepStage2.getString("pipeline_name"));
    }

    @Test
    public void testIntegrationPipelineQueueTimeOnStages() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationQueueTimeOnStages");
        String definition = getPipelineDefinition("testPipelineQueueOnStages.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));

        // schedule build and wait for it to get queued
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        // add some delay before creating the worker:
        // the build stages will be waiting in the queue until the worker is available
        Thread.sleep(10000);
        final DumbSlave worker = jenkinsRule.createOnlineSlave(Label.get("testStage"));

        clientStub.waitForTraces(6);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(6, spans.size());

        final TraceSpan buildSpan = searchSpan(spans, "pipelineIntegrationQueueTimeOnStages");
        assertEquals(Double.valueOf(0), buildSpan.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals("built-in", buildSpan.getMeta().get(CITags.NODE_NAME));
        assertEquals("[\"built-in\"]", buildSpan.getMeta().get(CITags.NODE_LABELS));

        final TraceSpan runStages = searchSpan(spans, "Run stages");
        assertEquals(Double.valueOf(0), runStages.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals("built-in", runStages.getMeta().get(CITags.NODE_NAME));
        assertEquals("[\"built-in\"]", runStages.getMeta().get(CITags.NODE_LABELS));

        final TraceSpan stage1 = searchSpan(spans, "Stage 1");
        final Double stage1QueueTime = stage1.getMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage1QueueTime > 0L);
        assertTrue(stage1QueueTime > TimeUnit.NANOSECONDS.toSeconds(stage1.getDurationNano()));
        assertTrue(stage1.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), stage1.getMeta().get(CITags.NODE_NAME));
        assertTrue(stage1.getMeta().get(CITags.NODE_LABELS).contains("testStage"));

        final TraceSpan stepStage1 = searchFirstChild(spans, stage1);
        assertEquals(Double.valueOf(0), stepStage1.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stepStage1.getMeta().get(CITags.NODE_NAME));
        assertTrue(stepStage1.getMeta().get(CITags.NODE_LABELS).contains("testStage"));

        final TraceSpan stage2 = searchSpan(spans, "Stage 2");
        final Double stage2QueueTime = stage2.getMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage2QueueTime > 0L);
        assertTrue(stage2QueueTime > TimeUnit.NANOSECONDS.toSeconds(stage2.getDurationNano()));
        assertTrue(stage2.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), stage2.getMeta().get(CITags.NODE_NAME));
        assertTrue(stage2.getMeta().get(CITags.NODE_LABELS).contains("testStage"));

        final TraceSpan stepStage2 = searchFirstChild(spans, stage2);
        assertEquals(Double.valueOf(0), stepStage2.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stepStage2.getMeta().get(CITags.NODE_NAME));
        assertTrue(stepStage2.getMeta().get(CITags.NODE_LABELS).contains("testStage"));
    }

    private TraceSpan searchFirstChild(List<TraceSpan> spans, TraceSpan parentSpan) {
        for(TraceSpan span : spans) {
            if(span.context().getParentId() == parentSpan.context().getSpanId()) {
                return span;
            }
        }
        return null;
    }

    private JSONObject searchFirstChild(List<JSONObject> webhooks, JSONObject parentWebhook) {
        for(JSONObject webhook : webhooks) {
            if(webhook.has("parent_span_id") && webhook.get("parent_span_id").equals(parentWebhook.get("span_id"))) {
                return webhook;
            }
        }
        return null;
    }

    private TraceSpan searchSpan(List<TraceSpan> spans, String resourceName) {
        for(TraceSpan span : spans) {
            if(resourceName.equalsIgnoreCase(span.getResourceName())) {
                return span;
            }
        }
        return null;
    }

    private JSONObject searchWebhook(List<JSONObject> webhooks, String resourceName) {
        for(JSONObject webhook : webhooks) {
            if (resourceName.equalsIgnoreCase(webhook.getString("name"))) {
                return webhook;
            }
        }
        return null;
    }

    private JSONObject searchWebhookByLevel(List<JSONObject> webhooks, String level) {
        for(JSONObject webhook : webhooks) {
            if (level.equalsIgnoreCase(webhook.getString("level"))) {
                return webhook;
            }
        }
        return null;
    }
    @Test
    public void testIntegrationPipelineQueueTimeOnPipeline() throws Exception {
        final EnvironmentVariablesNodeProperty envProps = new EnvironmentVariablesNodeProperty();
        EnvVars env = envProps.getEnvVars();
        env.put("NODE_NAME", "testPipeline");
        jenkinsRule.jenkins.getGlobalNodeProperties().add(envProps);
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationQueueTimeOnPipeline");
        String definition = getPipelineDefinition("testPipelineQueueOnPipeline.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));

        // schedule build and wait for it to get queued
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(15000);
        final DumbSlave worker = jenkinsRule.createOnlineSlave(Label.get("testPipeline"));

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Double queueTime = buildSpan.getMetrics().get(CITags.QUEUE_TIME);
        assertTrue(queueTime > 0L);
        assertTrue(queueTime > TimeUnit.NANOSECONDS.toSeconds(buildSpan.getDurationNano()));
        assertTrue(buildSpan.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), buildSpan.getMeta().get(CITags.NODE_NAME));
        assertTrue(buildSpan.getMeta().get(CITags.NODE_LABELS).contains("testPipeline"));
        assertNotNull(buildSpan.getMeta().get(CITags._DD_HOSTNAME));

        final TraceSpan stage = spans.get(1);
        assertEquals(Double.valueOf(0), stage.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stage.getMeta().get(CITags.NODE_NAME));
        assertTrue(stage.getMeta().get(CITags.NODE_LABELS).contains("testPipeline"));
        assertNotNull(stage.getMeta().get(CITags._DD_HOSTNAME));

        final TraceSpan step = spans.get(2);
        assertEquals(Double.valueOf(0), step.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), step.getMeta().get(CITags.NODE_NAME));
        assertTrue(step.getMeta().get(CITags.NODE_LABELS).contains("testPipeline"));
        assertNotNull(stage.getMeta().get(CITags._DD_HOSTNAME));
    }

    @Test
    public void testIntegrationNoFailureTag() throws Exception {
        jenkinsRule.createOnlineSlave();
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationSuccess");
        String definition = getPipelineDefinition("testPipelineSuccess.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        final WorkflowRun run = job.scheduleBuild2(0).get();
        String hostname = DatadogUtilities.getHostname(null);
        String[] tags = new String[]{
                "jenkins_url:" + DatadogUtilities.getJenkinsUrl(),
                "user_id:anonymous",
                "job:pipelineIntegrationSuccess",
                "result:SUCCESS",
                "stage_depth:0",
                "stage_name:test",
                "parent_stage_name:root"               
        };
        clientStub.assertMetric("jenkins.job.stage_duration", hostname, tags);
        clientStub.assertMetric("jenkins.job.stage_pause_duration", 0, hostname, tags);

        final String buildPrefix = PipelineStepData.StepType.PIPELINE.getTagName();
        final String stagePrefix = PipelineStepData.StepType.STAGE.getTagName();
        final String stepPrefix = PipelineStepData.StepType.STEP.getTagName();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> buildSpanMeta = buildSpan.getMeta();
        assertEquals("jenkins.build", buildSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, buildSpan.getServiceName());
        assertEquals(ORIGIN_CIAPP_PIPELINE, buildSpanMeta.get(CITags._DD_ORIGIN));
        assertEquals("pipelineIntegrationSuccess", buildSpan.getResourceName());
        assertEquals("ci", buildSpan.getType());
        assertEquals("anonymous", buildSpanMeta.get(CITags.USER_NAME));
        assertEquals("jenkins", buildSpanMeta.get(CITags.CI_PROVIDER_NAME));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", buildSpanMeta.get(buildPrefix + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", buildSpanMeta.get(buildPrefix + CITags._NAME));
        assertEquals("1", buildSpanMeta.get(buildPrefix + CITags._NUMBER));
        assertEquals("success", buildSpanMeta.get(buildPrefix + CITags._RESULT));
        assertEquals("success", buildSpanMeta.get(CITags.STATUS));
        assertNotNull(buildSpanMeta.get(buildPrefix + CITags._URL));
        assertNotNull(buildSpanMeta.get(CITags.NODE_NAME));
        assertNotNull(buildSpanMeta.get(CITags.NODE_LABELS));
        checkHostNameTag(buildSpanMeta);
        assertEquals("success", buildSpanMeta.get(CITags.JENKINS_RESULT));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", buildSpanMeta.get(CITags.JENKINS_TAG));
        assertEquals("false", buildSpanMeta.get(CITags._DD_CI_INTERNAL));
        assertEquals(PipelineStepData.StepType.PIPELINE.getBuildLevel(), buildSpanMeta.get(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(PipelineStepData.StepType.PIPELINE.getBuildLevel(), buildSpanMeta.get(CITags._DD_CI_LEVEL));
        assertNotNull(buildSpanMeta.get(CITags._DD_CI_STAGES));
        assertTrue(buildSpanMeta.get(CITags._DD_CI_STAGES).contains("{\"name\":\"test\",\"duration\""));

        final TraceSpan stageSpan = spans.get(1);
        final Map<String, String> stageSpanMeta = stageSpan.getMeta();
        assertEquals("jenkins.stage", stageSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, stageSpan.getServiceName());
        assertEquals(ORIGIN_CIAPP_PIPELINE, stageSpanMeta.get(CITags._DD_ORIGIN));
        assertEquals("test", stageSpan.getResourceName());
        assertEquals("ci", stageSpan.getType());
        assertEquals("test", stageSpanMeta.get(stagePrefix + CITags._NAME));
        assertEquals("success", stageSpanMeta.get(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stageSpanMeta.get(CITags.CI_PROVIDER_NAME));
        assertNotNull(stageSpanMeta.get(stagePrefix + CITags._URL));
        assertNotNull(stageSpanMeta.get(CITags.NODE_NAME));
        assertNotNull(stageSpanMeta.get(CITags.NODE_LABELS));
        checkHostNameTag(stageSpanMeta);
        assertEquals("false", stageSpanMeta.get(CITags._DD_CI_INTERNAL));
        assertEquals("4", stageSpanMeta.get(stagePrefix + CITags._NUMBER));
        assertEquals(PipelineStepData.StepType.STAGE.getBuildLevel(), stageSpanMeta.get(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(PipelineStepData.StepType.STAGE.getBuildLevel(), stageSpanMeta.get(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stageSpanMeta.get(PipelineStepData.StepType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stageSpanMeta.get(PipelineStepData.StepType.PIPELINE.getTagName() + CITags._NAME));
        assertNotNull(stageSpan.getMetrics().get(CITags.QUEUE_TIME));

        final TraceSpan stepSpan = spans.get(2);
        final Map<String, String> stepSpanMeta = stepSpan.getMeta();
        assertEquals("jenkins.step", stepSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, stepSpan.getServiceName());
        assertEquals(ORIGIN_CIAPP_PIPELINE, stepSpanMeta.get(CITags._DD_ORIGIN));
        assertEquals("Print Message", stepSpan.getResourceName());
        assertEquals("ci", stepSpan.getType());
        assertEquals("Print Message", stepSpanMeta.get(stepPrefix + CITags._NAME));
        assertEquals("success", stepSpanMeta.get(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stepSpanMeta.get(CITags.CI_PROVIDER_NAME));
        assertEquals("hello", stepSpanMeta.get("jenkins.step.args.message"));
        assertNotNull(stepSpanMeta.get(stepPrefix + CITags._URL));
        assertNotNull(stepSpanMeta.get(stepPrefix + CITags._URL));
        assertNotNull(stepSpanMeta.get(CITags.NODE_NAME));
        assertNotNull(stepSpanMeta.get(CITags.NODE_LABELS));
        checkHostNameTag(stepSpanMeta);
        assertEquals("false", stepSpanMeta.get(CITags._DD_CI_INTERNAL));
        assertEquals("5", stepSpanMeta.get(stepPrefix + CITags._NUMBER));
        assertEquals(PipelineStepData.StepType.STEP.getBuildLevel(), stepSpanMeta.get(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(PipelineStepData.StepType.STEP.getBuildLevel(), stepSpanMeta.get(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stepSpanMeta.get(PipelineStepData.StepType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stepSpanMeta.get(PipelineStepData.StepType.PIPELINE.getTagName() + CITags._NAME));
        assertEquals("test", stepSpanMeta.get(PipelineStepData.StepType.STAGE.getTagName() + CITags._NAME));
        assertNotNull(stepSpan.getMetrics().get(CITags.QUEUE_TIME));

        assertCleanupActions(run);
    }

    @Test
    public void testIntegrationPipelineSkippedLogic() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration-SkippedLogic");
        String definition = getPipelineDefinition("testPipelineSkippedLogic.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(2);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(2, spans.size());

        final TraceSpan stage = spans.get(1);
        assertEquals("Stage", stage.getResourceName());
        assertEquals("skipped", stage.getMeta().get(CITags.STATUS));
    }

    @Test
    public void testIntegrationPipelineSkippedLogicWebhook() throws Exception {
        clientStub.configureForWebhooks();

        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration-SkippedLogicWebhook");
        String definition = getPipelineDefinition("testPipelineSkippedLogic.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(2);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(2, webhooks.size());

        final JSONObject stage = searchWebhookByLevel(webhooks, "stage");
        assertNotNull("Could not find stage webhook", stage);
        assertEquals("Stage", stage.getString("name"));
        assertEquals("skipped", stage.getString("status"));
    }

    @Test
    public void testIntegrationTracesDisabled() throws Exception{
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(false);

        jenkinsRule.createOnlineSlave();
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationSuccess-notraces");
        String definition = getPipelineDefinition("testPipelineSuccess.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();
        String hostname = DatadogUtilities.getHostname(null);
        String[] tags = new String[]{
                "jenkins_url:" + DatadogUtilities.getJenkinsUrl(),
                "user_id:anonymous",
                "job:pipelineIntegrationSuccess-notraces",
                "result:SUCCESS",
                "stage_depth:0",
                "stage_name:test",
                "parent_stage_name:root"
        };
        clientStub.assertMetric("jenkins.job.stage_duration", hostname, tags);
        clientStub.assertMetric("jenkins.job.stage_pause_duration", 0, hostname, tags);

        clientStub.waitForTraces(0);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(0, spans.size());
    }

    @Test
    public void getStageNameTest() {
        String stageName = "Hello world";
        StepStartNode node = mock(StepStartNode.class);
        when(node.getDisplayName()).thenReturn(stageName);

        // Regular stage with no thread
        when(node.getAction(ThreadNameAction.class)).thenReturn(null);
        assertEquals(listener.getStageName(node), stageName);

        // Parallel stage
        String stageNameThread = "Hello thread";
        ThreadNameAction threadNameAction = mock(ThreadNameAction.class);
        when(threadNameAction.getThreadName()).thenReturn(stageNameThread);
        when(node.getAction(ThreadNameAction.class)).thenReturn(threadNameAction);
        assertEquals(listener.getStageName(node), stageNameThread);
    }


    @Test
    public void getTimeTest() {
        long startTime = 10L, endTime = 12345L;

        TimingAction startAction = mock(TimingAction.class);
        when(startAction.getStartTime()).thenReturn(startTime);
        TimingAction endAction = mock(TimingAction.class);
        when(endAction.getStartTime()).thenReturn(endTime);

        StepStartNode startNode = mock(StepStartNode.class);
        when(startNode.getAction(TimingAction.class)).thenReturn(startAction);
        StepEndNode endNode = mock(StepEndNode.class);
        when(endNode.getAction(TimingAction.class)).thenReturn(endAction);

        assertEquals(endTime - startTime, listener.getTime(startNode, endNode));
    }

    @Test
    public void testStagesNodeNames_complexPipelineStages01() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "complexPipelineStages01");
        String definition = getPipelineDefinition("testPipelineComplexStages01.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        // schedule build and wait for it to get queued
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        final DumbSlave worker01 = jenkinsRule.createOnlineSlave(Label.get("worker01"));
        final DumbSlave worker02 = jenkinsRule.createOnlineSlave(Label.get("worker02"));
        final DumbSlave worker03 = jenkinsRule.createOnlineSlave(Label.get("worker03"));

        clientStub.waitForTraces(19);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(19, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertEquals(worker03.getNodeName(), buildSpan.getMeta().get(CITags.NODE_NAME));
        assertTrue(buildSpan.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan prepareBlock = spans.get(1);
        assertEquals("Prepare", prepareBlock.getResourceName());

        final TraceSpan prepareStage01 = spans.get(2);
        assertNodeNameParallelBlock(prepareStage01, worker01, worker02);

        final TraceSpan prepareStage02 = spans.get(3);
        assertNodeNameParallelBlock(prepareStage02, worker01, worker02);

        final TraceSpan prepareStep01 = spans.get(4);
        assertNoneNameParallelStep(prepareStep01, prepareStage01, prepareStage02);

        final TraceSpan prepareStep02 = spans.get(5);
        assertNoneNameParallelStep(prepareStep02, prepareStage01, prepareStage02);

        final TraceSpan installStage = spans.get(6);
        assertEquals("Install", installStage.getResourceName());
        assertEquals(worker03.getNodeName(), installStage.getMeta().get(CITags.NODE_NAME));
        assertTrue(installStage.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan installStep = spans.get(7);
        assertEquals(worker03.getNodeName(), installStep.getMeta().get(CITags.NODE_NAME));
        assertTrue(installStep.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan bumpVersionStage = spans.get(8);
        assertEquals("Bump version", bumpVersionStage.getResourceName());
        assertEquals(worker03.getNodeName(), bumpVersionStage.getMeta().get(CITags.NODE_NAME));
        assertTrue(bumpVersionStage.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan bumpVersionStep = spans.get(9);
        assertEquals(worker03.getNodeName(), bumpVersionStep.getMeta().get(CITags.NODE_NAME));
        assertTrue(bumpVersionStep.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan buildStage = spans.get(10);
        assertEquals("Build", buildStage.getResourceName());
        assertEquals(worker03.getNodeName(), buildStage.getMeta().get(CITags.NODE_NAME));
        assertTrue(buildStage.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan buildStep = spans.get(11);
        assertEquals(worker03.getNodeName(), buildStep.getMeta().get(CITags.NODE_NAME));
        assertTrue(buildStep.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan validateBlock = spans.get(12);
        assertEquals("Validate", validateBlock.getResourceName());
        assertEquals(worker03.getNodeName(), validateBlock.getMeta().get(CITags.NODE_NAME));
        assertTrue(validateBlock.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan validateStage01 = spans.get(13);
        assertNodeNameParallelBlock(validateStage01, worker01, worker02);

        final TraceSpan validateStage02 = spans.get(14);
        assertNodeNameParallelBlock(validateStage02, worker01, worker02);

        final TraceSpan validateStep01 = spans.get(15);
        assertNoneNameParallelStep(validateStep01, validateStage01, validateStage02);

        final TraceSpan validateStep02 = spans.get(16);
        assertNoneNameParallelStep(validateStep02, validateStage01, validateStage02);

        final TraceSpan ciStage = spans.get(17);
        assertEquals("CI", ciStage.getResourceName());
        assertEquals(worker03.getNodeName(), ciStage.getMeta().get(CITags.NODE_NAME));
        assertTrue(ciStage.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan ciStep = spans.get(18);
        assertEquals(worker03.getNodeName(), ciStep.getMeta().get(CITags.NODE_NAME));
        assertTrue(ciStep.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));
    }

    @Test
    public void testGlobalTagsPropagationsTraces() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setGlobalJobTags("(.*?)_job, global_job_tag:$ENV_VAR");
        cfg.setGlobalTags("global_tag:$ENV_VAR");
        EnvVars.masterEnvVars.put("ENV_VAR", "value");

        jenkinsRule.createOnlineSlave(new LabelAtom("testGlobalTags"));
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration-GlobalTagsPropagation_job");
        String definition = getPipelineDefinition("testPipelineGlobalTags.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Map<String, String> buildSpanMeta = buildSpan.getMeta();
        assertEquals("value", buildSpanMeta.get("global_tag"));
        assertEquals("value", buildSpanMeta.get("global_job_tag"));
        assertEquals("pipeline_tag_v2", buildSpanMeta.get("pipeline_tag"));
        assertEquals("pipeline_tag", buildSpanMeta.get("pipeline_tag_v2"));

        final TraceSpan stageSpan = spans.get(1);
        final Map<String, String> stageSpanMeta = stageSpan.getMeta();
        assertEquals("value", stageSpanMeta.get("global_tag"));
        assertEquals("value", stageSpanMeta.get("global_job_tag"));
        assertEquals("pipeline_tag_v2", stageSpanMeta.get("pipeline_tag"));
        assertEquals("pipeline_tag", stageSpanMeta.get("pipeline_tag_v2"));

        final TraceSpan stepSpan = spans.get(2);
        final Map<String, String> stepSpanMeta = stepSpan.getMeta();
        assertEquals("value", stepSpanMeta.get("global_tag"));
        assertEquals("value", stepSpanMeta.get("global_job_tag"));
        assertEquals("pipeline_tag_v2", stepSpanMeta.get("pipeline_tag"));
        assertEquals("pipeline_tag", stepSpanMeta.get("pipeline_tag_v2"));
    }

    @Test
    public void testCurrentlyBuildingJobsMetric() throws Exception{
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testCurrentJobMetricPipeline");
        String definition = getPipelineDefinition("testCurrentJobMetric.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));

        QueueTaskFuture<WorkflowRun> buildFuture = job.scheduleBuild2(0);
        Future<WorkflowRun> buildStartFuture = buildFuture.getStartCondition();
        buildStartFuture.get(); // waiting for build to start

        // we know the pipeline is in progress at this point because it cannot complete until 'test-current-metric-agent' node goes online
        DatadogComputerPublisher.publishMetrics(clientStub);

        DumbSlave agent = jenkinsRule.createOnlineSlave(Label.get("test-current-metric-agent"));

        String hostname = DatadogUtilities.getHostname(null);
        String[] tags = new String[]{ "jenkins_url:" + DatadogUtilities.getJenkinsUrl() };
        clientStub.assertMetric("jenkins.job.currently_building", 1, hostname, tags);

        buildFuture.get(); // waiting for build to finish
        jenkinsRule.disconnectSlave(agent); // disconnecting the agent node
    }

    @Test
    public void testErrorPropagationOnStages() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration-errorPropagationStages");
        String definition = getPipelineDefinition(getFailingPipelineDefinitionName());
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());

        final TraceSpan stepSpan = spans.get(2);
        assertEquals(true, stepSpan.isError());
        assertEquals(CITags.STATUS_ERROR, stepSpan.getMeta().get(CITags.STATUS));
        assertEquals(stepSpan.getMeta().get("error.stack"), "hudson.AbortException", stepSpan.getMeta().get(CITags.ERROR_TYPE));

        final TraceSpan stageSpan = spans.get(1);
        assertEquals(true, stageSpan.isError());
        assertEquals(CITags.STATUS_ERROR, stageSpan.getMeta().get(CITags.STATUS));

        final TraceSpan buildSpan = spans.get(0);
        assertEquals(true, buildSpan.isError());
        assertEquals(CITags.STATUS_ERROR, buildSpan.getMeta().get(CITags.STATUS));
    }

    @Test
    public void testErrorPropagationOnStagesWebhook() throws Exception {
        clientStub.configureForWebhooks();

        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration-errorPropagationStagesWebhook");
        String definition = getPipelineDefinition(getFailingPipelineDefinitionName());
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(3);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(3, webhooks.size());

        final JSONObject step = searchWebhookByLevel(webhooks, "job");
        assertEquals(CITags.STATUS_ERROR, step.getString("status"));
        final JSONObject stepError = step.getJSONObject("error");
        assertEquals("hudson.AbortException", stepError.getString("type"));

        final JSONObject stage = searchWebhookByLevel(webhooks, "stage");
        assertEquals(CITags.STATUS_ERROR, stage.getString("status"));

        final JSONObject pipeline = searchWebhookByLevel(webhooks, "pipeline");
        assertEquals(CITags.STATUS_ERROR, pipeline.getString("status"));
    }

    // need to use a different pipeline on Windows, since "sh" command is not supported there,
    // and when trying to run a shell script the error is different from the one that we're modelling in our tests
    private String getFailingPipelineDefinitionName() {
        return isRunningOnWindows() ? "testPipelineErrorOnStagesOnWindows.txt" : "testPipelineErrorOnStages.txt";
    }

    private static boolean isRunningOnWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    @Test
    public void testUnstablePropagationOnStages() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration-unstablePropagationStages");
        String definition = getPipelineDefinition("testPipelineUnstableOnStages.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());

        final TraceSpan stepSpan = spans.get(2);
        assertEquals(true, stepSpan.isError());
        assertEquals(CITags.STATUS_UNSTABLE, stepSpan.getMeta().get(CITags.STATUS));
        assertEquals("unstable", stepSpan.getMeta().get(CITags.ERROR_TYPE));

        final TraceSpan stageSpan = spans.get(1);
        assertEquals(true, stageSpan.isError());
        assertEquals(CITags.STATUS_UNSTABLE, stageSpan.getMeta().get(CITags.STATUS));

        final TraceSpan buildSpan = spans.get(0);
        assertEquals(true, buildSpan.isError());
        assertEquals(CITags.STATUS_UNSTABLE, buildSpan.getMeta().get(CITags.STATUS));
    }

    @Test
    public void testUnstablePropagationOnStagesWebhook() throws Exception {
        clientStub.configureForWebhooks();

        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration-unstablePropagationStagesWebhook");
        String definition = getPipelineDefinition("testPipelineUnstableOnStages.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        clientStub.waitForWebhooks(3);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(3, webhooks.size());

        final JSONObject step = searchWebhookByLevel(webhooks, "job");
        assertEquals(CITags.STATUS_UNSTABLE, step.getString("status"));
        final JSONObject stepError = step.getJSONObject("error");
        assertEquals("unstable", stepError.getString("type"));

        final JSONObject stage = searchWebhookByLevel(webhooks, "stage");
        assertEquals(CITags.STATUS_UNSTABLE, stage.getString("status"));

        final JSONObject pipeline = searchWebhookByLevel(webhooks, "pipeline");
        assertEquals(CITags.STATUS_UNSTABLE, pipeline.getString("status"));
    }

    @Test
    public void testCustomHostnameForWorkers() throws Exception {
        final EnvironmentVariablesNodeProperty envProps = new EnvironmentVariablesNodeProperty();
        EnvVars env = envProps.getEnvVars();
        env.put("NODE_NAME", "testPipeline");
        env.put("DD_CI_HOSTNAME", "testDDCiHostname");
        jenkinsRule.jenkins.getGlobalNodeProperties().add(envProps);
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationCustomHostname");
        String definition = getPipelineDefinition("testPipelineOnWorkers.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));

        // schedule build and wait for it to get queued
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        final DumbSlave worker = jenkinsRule.createOnlineSlave(Label.get("testPipelineWorker"));

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertEquals(worker.getNodeName(), buildSpan.getMeta().get(CITags.NODE_NAME));
        assertTrue(buildSpan.getMeta().get(CITags.NODE_LABELS).contains("testPipelineWorker"));
        assertEquals("testDDCiHostname", buildSpan.getMeta().get(CITags._DD_HOSTNAME));

        final TraceSpan stage = spans.get(1);
        assertEquals(worker.getNodeName(), stage.getMeta().get(CITags.NODE_NAME));
        assertTrue(stage.getMeta().get(CITags.NODE_LABELS).contains("testPipelineWorker"));
        assertEquals("testDDCiHostname", stage.getMeta().get(CITags._DD_HOSTNAME));

        final TraceSpan step = spans.get(2);
        assertEquals(worker.getNodeName(), step.getMeta().get(CITags.NODE_NAME));
        assertTrue(step.getMeta().get(CITags.NODE_LABELS).contains("testPipelineWorker"));
        assertEquals("testDDCiHostname", step.getMeta().get(CITags._DD_HOSTNAME));

        jenkinsRule.jenkins.removeNode(worker);
        job.delete();
    }

    @Test
    public void testCustomHostnameForWorkersWebhook() throws Exception {
        clientStub.configureForWebhooks();

        final EnvironmentVariablesNodeProperty envProps = new EnvironmentVariablesNodeProperty();
        EnvVars env = envProps.getEnvVars();
        env.put("NODE_NAME", "testPipeline");
        env.put("DD_CI_HOSTNAME", "testDDCiHostname");
        jenkinsRule.jenkins.getGlobalNodeProperties().add(envProps);
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationCustomHostnameWebhook");
        String definition = getPipelineDefinition("testPipelineOnWorkersWebhook.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));

        // schedule build and wait for it to get queued
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        final DumbSlave worker = jenkinsRule.createOnlineSlave(Label.get("testPipelineWorkerWebhook"));

        clientStub.waitForWebhooks(3);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(3, webhooks.size());

        final JSONObject pipeline = webhooks.get(0);
        final JSONObject pipelineNode = pipeline.getJSONObject("node");
        assertEquals(worker.getNodeName(), pipelineNode.getString("name"));
        assertEquals("testDDCiHostname", pipelineNode.getString("hostname"));
        assertTrue(pipelineNode.getJSONArray("labels").contains("testPipelineWorkerWebhook"));

        final JSONObject stage = webhooks.get(1);
        final JSONObject stageNode = stage.getJSONObject("node");
        assertEquals(worker.getNodeName(), stageNode.getString("name"));
        assertEquals("testDDCiHostname", stageNode.getString("hostname"));
        assertTrue(stageNode.getJSONArray("labels").contains("testPipelineWorkerWebhook"));


        final JSONObject step = webhooks.get(2);
        final JSONObject stepNode = step.getJSONObject("node");
        assertEquals(worker.getNodeName(), stepNode.getString("name"));
        assertEquals("testDDCiHostname", stepNode.getString("hostname"));
        assertTrue(stepNode.getJSONArray("labels").contains("testPipelineWorkerWebhook"));

        jenkinsRule.jenkins.removeNode(worker);
        job.delete();
    }

    @Test
    public void testCustomHostnameForWorkersInFreestyleJob() throws Exception {
        final EnvironmentVariablesNodeProperty envProps = new EnvironmentVariablesNodeProperty();
        EnvVars env = envProps.getEnvVars();
        env.put("NODE_NAME", "testPipeline");
        env.put("DD_CI_HOSTNAME", "testDDCiHostname");
        jenkinsRule.jenkins.getGlobalNodeProperties().add(envProps);
        FreeStyleProject job = jenkinsRule.createFreeStyleProject("freestylah");
        job.setAssignedLabel(Label.get("freestylah"));

        final DumbSlave worker = jenkinsRule.createOnlineSlave(Label.get("freestylah"));

        // schedule build and wait for it to get queued
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        clientStub.waitForTraces(1);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(1, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertEquals(worker.getNodeName(), buildSpan.getMeta().get(CITags.NODE_NAME));
        assertTrue(buildSpan.getMeta().get(CITags.NODE_LABELS).contains("freestylah"));
        assertEquals("testDDCiHostname", buildSpan.getMeta().get(CITags._DD_HOSTNAME));

        jenkinsRule.jenkins.removeNode(worker);
        job.delete();

    }

    @Test
    public void testCustomHostnameForWorkersInFreestyleJobWebhook() throws Exception {
        clientStub.configureForWebhooks();

        final EnvironmentVariablesNodeProperty envProps = new EnvironmentVariablesNodeProperty();
        EnvVars env = envProps.getEnvVars();
        env.put("NODE_NAME", "testPipelineWorkerWebhook");
        env.put("DD_CI_HOSTNAME", "testDDCiHostname");
        jenkinsRule.jenkins.getGlobalNodeProperties().add(envProps);
        FreeStyleProject job = jenkinsRule.createFreeStyleProject("freestylahWebhook");
        job.setAssignedLabel(Label.get("freestylahWebhook"));

        final DumbSlave worker = jenkinsRule.createOnlineSlave(Label.get("freestylahWebhook"));

        // schedule build and wait for it to get queued
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        clientStub.waitForWebhooks(1);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(1, webhooks.size());

        final JSONObject pipeline = webhooks.get(0);
        final JSONObject pipelineNode = pipeline.getJSONObject("node");
        assertEquals(worker.getNodeName(), pipelineNode.getString("name"));
        assertEquals("testDDCiHostname", pipelineNode.getString("hostname"));
        assertTrue(pipelineNode.getJSONArray("labels").contains("freestylahWebhook"));

        jenkinsRule.jenkins.removeNode(worker);
        job.delete();

    }

    private void assertNodeNameParallelBlock(TraceSpan stageSpan, DumbSlave worker01, DumbSlave worker02) {
        switch (stageSpan.getResourceName()){
            case "Prepare01":
            case "Validate01":
                assertEquals(worker01.getNodeName(), stageSpan.getMeta().get(CITags.NODE_NAME));
                assertTrue(((String) stageSpan.getMeta().get(CITags.NODE_LABELS)).contains(worker01.getNodeName()));
                break;
            case "Prepare02":
            case "Validate02":
                assertEquals(worker02.getNodeName(), stageSpan.getMeta().get(CITags.NODE_NAME));
                assertTrue(stageSpan.getMeta().get(CITags.NODE_LABELS).contains(worker02.getNodeName()));
                break;
        }
    }

    private void assertNoneNameParallelStep(TraceSpan step, TraceSpan stage01, TraceSpan stage02) {
        if(stage01.context().getSpanId() == step.context().getParentId()) {
            assertEquals(stage01.getMeta().get(CITags.NODE_NAME), step.getMeta().get(CITags.NODE_NAME));
            assertEquals(stage01.getMeta().get(CITags.NODE_LABELS), step.getMeta().get(CITags.NODE_LABELS));
        } else if(stage02.context().getSpanId() == step.context().getParentId()){
            assertEquals(stage02.getMeta().get(CITags.NODE_NAME), step.getMeta().get(CITags.NODE_NAME));
            assertEquals(stage02.getMeta().get(CITags.NODE_LABELS), step.getMeta().get(CITags.NODE_LABELS));
        } else {
            fail("Unknown parent stage");
        }
    }

    @Test
    public void testIsManualTrue() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationIsManualTrue");
        String definition = getPipelineDefinition("testPipelineSuccess.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        CauseAction causeAction = new CauseAction(new TimerTriggerCause(), new UserIdCause("johanna"));
        job.scheduleBuild2(0, causeAction).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        final TraceSpan buildSpan = spans.get(0);
        final String isManual = buildSpan.getMeta().get(CITags.IS_MANUAL);
        assertEquals("true", isManual);
    }


    @Test
    public void testIsManualTrueWebhooks() throws Exception {
        clientStub.configureForWebhooks();
        Jenkins jenkins = jenkinsRule.jenkins;
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationIsManualTrueWebhook");
        String definition = getPipelineDefinition("testPipelineSuccess.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        CauseAction causeAction = new CauseAction(new TimerTriggerCause(), new UserIdCause("johanna"));
        job.scheduleBuild2(0, causeAction).get();

        clientStub.waitForWebhooks(3);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(3, webhooks.size());
        JSONObject pipeline = searchWebhookByLevel(webhooks, "pipeline");
        assertTrue(pipeline.getBoolean("is_manual"));
    }

    @Test
    public void testIsManualFalse() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationIsManualFalse");
        String definition = getPipelineDefinition("testPipelineSuccess.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        CauseAction causeAction = new CauseAction(new TimerTriggerCause(), new SCMTriggerCause("scm"));
        job.scheduleBuild2(0, causeAction).get();

        clientStub.waitForTraces(3);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(3, spans.size());
        final TraceSpan buildSpan = getBuild(spans);
        final String isManual = buildSpan.getMeta().get(CITags.IS_MANUAL);
        assertEquals("false", isManual);
    }

    private TraceSpan getBuild(List<TraceSpan> spans) {
        for (TraceSpan span : spans) {
            if ("jenkins.build".equals(span.getOperationName())) {
                return span;
            }
        }
        return null;
    }

    @Test
    public void testIsManualFalseWebhooks() throws Exception {
        clientStub.configureForWebhooks();
        Jenkins jenkins = jenkinsRule.jenkins;
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationIsManualFalseWebhook");
        String definition = getPipelineDefinition("testPipelineSuccess.txt");
        job.setDefinition(new CpsFlowDefinition(definition, true));
        CauseAction causeAction = new CauseAction(new TimerTriggerCause(), new SCMTriggerCause("scm"));
        job.scheduleBuild2(0, causeAction).get();

        clientStub.waitForWebhooks(3);
        final List<JSONObject> webhooks = clientStub.getWebhooks();
        assertEquals(3, webhooks.size());
        final JSONObject pipeline = searchWebhookByLevel(webhooks, "pipeline");
        assertFalse(pipeline.getBoolean("is_manual"));
    }

    @Test
    public void testErrorPropagationOnNestedStages() throws Exception {
        givenPipeline("testErrorPropagationOnNestedStages", "testErrorPropagationOnNestedStages.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testErrorPropagationOnNestedStages", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Outer Stage", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Inner Stage", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Shell Script", true, CITags.STATUS_ERROR);
    }

    @Test
    public void testErrorPropagationOnNestedStagesCatchFailureFailure() throws Exception {
        givenPipeline("testErrorPropagationOnNestedStagesCatchFailureFailure", "testErrorPropagationOnNestedStagesCatchFailureFailure.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testErrorPropagationOnNestedStagesCatchFailureFailure", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Outer Stage", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Inner Stage", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Shell Script", true, CITags.STATUS_ERROR);
    }

    @Test
    public void testErrorPropagationOnNestedStagesCatchUnstableFailure() throws Exception {
        givenPipeline("testErrorPropagationOnNestedStagesCatchUnstableFailure", "testErrorPropagationOnNestedStagesCatchUnstableFailure.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testErrorPropagationOnNestedStagesCatchUnstableFailure", true, CITags.STATUS_UNSTABLE);
        assertSpan(spans, "Outer Stage", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Inner Stage", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Shell Script", true, CITags.STATUS_ERROR);
    }

    @Test
    public void testErrorPropagationOnNestedStagesCatchSuccessFailure() throws Exception {
        givenPipeline("testErrorPropagationOnNestedStagesCatchSuccessFailure", "testErrorPropagationOnNestedStagesCatchSuccessFailure.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testErrorPropagationOnNestedStagesCatchSuccessFailure", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Outer Stage", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Inner Stage", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Shell Script", true, CITags.STATUS_ERROR);
    }

    @Test
    public void testErrorPropagationOnNestedStagesCatchSuccessUnstable() throws Exception {
        givenPipeline("testErrorPropagationOnNestedStagesCatchSuccessUnstable", "testErrorPropagationOnNestedStagesCatchSuccessUnstable.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testErrorPropagationOnNestedStagesCatchSuccessUnstable", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Outer Stage", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Inner Stage", true, CITags.STATUS_UNSTABLE);
        assertSpan(spans, "Shell Script", true, CITags.STATUS_ERROR);
    }

    @Test
    public void testErrorPropagationOnNestedStagesCatchSuccessSuccess() throws Exception {
        givenPipeline("testErrorPropagationOnNestedStagesCatchSuccessSuccess", "testErrorPropagationOnNestedStagesCatchSuccessSuccess.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testErrorPropagationOnNestedStagesCatchSuccessSuccess", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Outer Stage", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Inner Stage", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Shell Script", true, CITags.STATUS_ERROR);
    }

    @Test
    public void testErrorPropagationOnNestedStagesScriptedCatchSuppress() throws Exception {
        givenPipeline("testErrorPropagationOnNestedStagesScriptedCatchSuppress", "testErrorPropagationOnNestedStagesScriptedCatchSuppress.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testErrorPropagationOnNestedStagesScriptedCatchSuppress", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Outer Stage", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Inner Stage", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Shell Script", true, CITags.STATUS_ERROR);
    }

    @Test
    public void testErrorPropagationOnNestedStagesScriptedCatchRethrow() throws Exception {
        givenPipeline("testErrorPropagationOnNestedStagesScriptedCatchRethrow", "testErrorPropagationOnNestedStagesScriptedCatchRethrow.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testErrorPropagationOnNestedStagesScriptedCatchRethrow", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Outer Stage", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Inner Stage", true, CITags.STATUS_ERROR);
        assertSpan(spans, "Shell Script", true, CITags.STATUS_ERROR);
    }

    @Test
    public void testErrorPropagationOnNestedStagesWarnError() throws Exception {
        givenPipeline("testErrorPropagationOnNestedStagesWarnError", "testErrorPropagationOnNestedStagesWarnError.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testErrorPropagationOnNestedStagesWarnError", true, CITags.STATUS_UNSTABLE);
        assertSpan(spans, "Outer Stage", false, CITags.STATUS_SUCCESS);
        assertSpan(spans, "Inner Stage", true, CITags.STATUS_UNSTABLE);
        assertSpan(spans, "Shell Script", true, CITags.STATUS_ERROR);
    }

    @Test
    public void testUnstablePropagationOnNestedStages() throws Exception {
        givenPipeline("testUnstablePropagationOnNestedStages", "testUnstablePropagationOnNestedStages.txt");
        final List<TraceSpan> spans = whenExecuting(4);
        assertSpan(spans, "testUnstablePropagationOnNestedStages", true, CITags.STATUS_UNSTABLE);
        assertSpan(spans, "Outer Stage", true, CITags.STATUS_UNSTABLE);
        assertSpan(spans, "Inner Stage", true, CITags.STATUS_UNSTABLE);
        assertSpan(spans, "Set stage result to unstable", true, CITags.STATUS_UNSTABLE);
    }

    private void givenPipeline(String name, String definitionPath) throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, name);
        String definition = getPipelineDefinition(definitionPath);
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();
    }

    private List<TraceSpan> whenExecuting(int expectedSpanCount) throws InterruptedException, TimeoutException {
        clientStub.waitForTraces(expectedSpanCount);
        final List<TraceSpan> spans = clientStub.getSpans();
        assertEquals(expectedSpanCount, spans.size());
        return spans;
    }

    private void assertSpan(List<TraceSpan> spans, String resourceName, boolean isError, String status) {
        for (TraceSpan span : spans) {
            if (resourceName.equals(span.getResourceName())) {
                assertEquals(isError, span.isError());
                assertEquals(status, span.getMeta().get(CITags.STATUS));
                return;
            }
        }
        fail("Span with resource name " + resourceName + " is not found in trace: " + spans);
    }

}
