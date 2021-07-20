package org.datadog.jenkins.plugins.datadog.listeners;

import static org.datadog.jenkins.plugins.datadog.traces.CITags.Values.ORIGIN_CIAPP_PIPELINE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import datadog.trace.common.writer.ListWriter;
import datadog.trace.core.DDSpan;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;
import org.datadog.jenkins.plugins.datadog.transport.FakeAgentHttpClient;
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
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DatadogGraphListenerTest extends DatadogTraceAbstractTest {

    private static final String SAMPLE_SERVICE_NAME = "sampleServiceName";

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
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
        ClientFactory.setTestClient(clientStub);
        clientStub.tracerWriter.start();

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
        String hostname = DatadogUtilities.getHostname(null);
        String[] expectedTags = new String[] { "jenkins_url:" + DatadogUtilities.getJenkinsUrl(), "user_id:anonymous",
                "stage_name:low", "job:pipeline", "parent_stage_name:medium", "stage_depth:2", "result:SUCCESS" };
        clientStub.assertMetric("jenkins.job.stage_duration", endTime - startTime, hostname, expectedTags);
        clientStub.assertMetric("jenkins.job.stage_pause_duration", 0, hostname, expectedTags);
        clientStub.assertMetric("jenkins.job.stage_completed", 1, hostname, expectedTags);
    }

    @Test
    public void testIntegration() throws Exception {
        jenkinsRule.createOnlineSlave(new LabelAtom("windows"));
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineDefinition.txt"),
                "UTF-8"
        );
        job.setDefinition(new CpsFlowDefinition(definition, true));
        WorkflowRun run = job.scheduleBuild2(0).get();
        BufferedReader br = new BufferedReader(run.getLogReader());
        String s;
        while ((s = br.readLine()) != null) {
            System.out.println(s);
        }
        br.close();
        String hostname = DatadogUtilities.getHostname(null);
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
            String[] expectedTags = Arrays.copyOf(baseTags, baseTags.length + 3);
            expectedTags[expectedTags.length - 3] = "stage_depth:" + depths[i];
            expectedTags[expectedTags.length - 2] = "stage_name:" + stageNames[i];
            expectedTags[expectedTags.length - 1] = "parent_stage_name:" + parentNames[i];
            clientStub.assertMetric("jenkins.job.stage_duration", hostname, expectedTags);

            if (stageNames[i] == "Test On Linux" || stageNames[i] == "Parallel tests") {
                // Timeout is set to 11s, but since there are other instructions,
                // we test it's at least 10s.
                double pauseValue = clientStub.assertMetricGetValue("jenkins.job.stage_pause_duration", hostname, expectedTags);
                assertTrue(pauseValue > 10000);
                assertTrue(pauseValue <= 11000);
            } else {
                clientStub.assertMetric("jenkins.job.stage_pause_duration", 0.0, hostname, expectedTags);
            }
        }

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(3);
        assertEquals(3, tracerWriter.size());

        //TODO Remove Java Tracer
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        //TODO Remove Java Tracer
        final List<DDSpan> stage01Trace = tracerWriter.get(1);
        assertEquals(2, stage01Trace.size());

        //TODO Remove Java Tracer
        final List<DDSpan> stage02Trace = tracerWriter.get(2);
        assertEquals(13, stage02Trace.size());

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(16);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(16, spans.size());
    }

    @Test
    public void testIntegrationGitInfo() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", "https://github.com/johndoe/foobar.git");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationSingleCommit");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineSuccess.txt"),
                "UTF-8"
        );

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());
        InputStream gitZip = getClass().getClassLoader().getResourceAsStream("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip");
        if(gitZip != null) {
            ws.unzipFrom(gitZip);
        }
        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpanOld = buildTrace.get(0);
        assertGitVariablesOld(buildSpanOld, "master");

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(3);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(3, spans.size());
        final TraceSpan buildSpan = spans.get(0);
        assertGitVariables(buildSpan, "master");
    }

    @Test
    public void testIntegrationGitInfoDefaultBranchEnvVar() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", "https://github.com/johndoe/foobar.git");
        final String defaultBranch = "refs/heads/hardcoded-master";
        env.put("DD_GIT_DEFAULT_BRANCH", defaultBranch);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationSingleCommitDefaultBranchEnvVar");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineSuccess.txt"),
                "UTF-8"
        );

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());
        InputStream gitZip = getClass().getClassLoader().getResourceAsStream("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip");
        if(gitZip != null) {
            ws.unzipFrom(gitZip);
        }
        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpanOld = buildTrace.get(0);
        assertGitVariablesOld(buildSpanOld, "hardcoded-master");

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(3);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(3, spans.size());
        final TraceSpan buildSpan = spans.get(0);
        assertGitVariables(buildSpan, "hardcoded-master");
    }

    @Test
    public void testIntegrationGitInfoOverrideCommit() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", "https://github.com/johndoe/foobar.git");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationOverrideCommit");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelinesOverrideGitCommit.txt"),
                "UTF-8"
        );

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());
        InputStream gitZip = getClass().getClassLoader().getResourceAsStream("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip");
        if(gitZip != null) {
            ws.unzipFrom(gitZip);
        }
        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(3);
        assertEquals(3, tracerWriter.size());
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", buildSpan.getTag(CITags.GIT_COMMIT_SHA));

        //TODO Remove Java Tracer
        final List<DDSpan> stage1Chain = tracerWriter.get(1);
        assertEquals(2, stage1Chain.size());
        final DDSpan stage1 = stage1Chain.get(0);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", stage1.getTag(CITags.GIT_COMMIT_SHA));
        final DDSpan step1 = stage1Chain.get(1);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", step1.getTag(CITags.GIT_COMMIT_SHA));

        //TODO Remove Java Tracer
        final List<DDSpan> stage2Chain = tracerWriter.get(2);
        assertEquals(2, stage2Chain.size());
        final DDSpan stage2 = stage2Chain.get(0);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", stage2.getTag(CITags.GIT_COMMIT_SHA));
        final DDSpan step2 = stage2Chain.get(1);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", step2.getTag(CITags.GIT_COMMIT_SHA));

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(5);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(5, spans.size());
        for(TraceSpan span : spans) {
            assertEquals("401d997a6eede777602669ccaec059755c98161f", span.getMeta().get(CITags.GIT_COMMIT_SHA));
        }
    }

    @Test
    public void testIntegrationGitAlternativeRepoUrl() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL_1", "https://github.com/johndoe/foobar.git");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipelineIntegrationAltRepoUrl");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelinesOverrideGitCommit.txt"),
                "UTF-8"
        );

        job.setDefinition(new CpsFlowDefinition(definition, true));
        final FilePath ws = jenkins.getWorkspaceFor(job);
        env.put("NODE_NAME", "master");
        env.put("WORKSPACE", ws.getRemote());
        InputStream gitZip = getClass().getClassLoader().getResourceAsStream("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip");
        if(gitZip != null) {
            ws.unzipFrom(gitZip);
        }
        jenkins.getGlobalNodeProperties().add(prop);
        job.scheduleBuild2(0).get();

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(3);
        assertEquals(3, tracerWriter.size());
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("https://github.com/johndoe/foobar.git", buildSpan.getTag(CITags.GIT_REPOSITORY_URL));

        //TODO Remove Java Tracer
        final List<DDSpan> stage1Chain = tracerWriter.get(1);
        assertEquals(2, stage1Chain.size());
        final DDSpan stage1 = stage1Chain.get(0);
        assertEquals("https://github.com/johndoe/foobar.git", stage1.getTag(CITags.GIT_REPOSITORY_URL));
        final DDSpan step1 = stage1Chain.get(1);
        assertEquals("https://github.com/johndoe/foobar.git", step1.getTag(CITags.GIT_REPOSITORY_URL));

        //TODO Remove Java Tracer
        final List<DDSpan> stage2Chain = tracerWriter.get(2);
        assertEquals(2, stage2Chain.size());
        final DDSpan stage2 = stage2Chain.get(0);
        assertEquals("https://github.com/johndoe/foobar.git", stage2.getTag(CITags.GIT_REPOSITORY_URL));
        final DDSpan step2 = stage2Chain.get(1);
        assertEquals("https://github.com/johndoe/foobar.git", step2.getTag(CITags.GIT_REPOSITORY_URL));

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(5);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(5, spans.size());
        for(TraceSpan span : spans) {
            assertEquals("https://github.com/johndoe/foobar.git", span.getMeta().get(CITags.GIT_REPOSITORY_URL));
        }
    }

    @Test
    public void testStageNamePropagation() throws Exception{
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationStages");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineStages.txt"),
                "UTF-8"
        );
        job.setDefinition(new CpsFlowDefinition(definition, true));
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        jenkinsRule.createOnlineSlave(Label.get("testStageName"));

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        //TODO Remove Java Tracer
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(5, pipelineTrace.size());

        //TODO Remove Java Tracer
        final DDSpan stage2Old = pipelineTrace.get(1);
        final String stage2OldName = (String) stage2Old.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage2OldName != null && !stage2OldName.isEmpty());

        //TODO Remove Java Tracer
        final DDSpan stepStage2Old = pipelineTrace.get(2);
        assertEquals(stage2OldName, stepStage2Old.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));

        //TODO Remove Java Tracer
        final DDSpan stage1Old = pipelineTrace.get(3);
        final String stage1OldName = (String) stage1Old.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage1OldName != null && !stage1OldName.isEmpty());

        //TODO Remove Java Tracer
        final DDSpan stepStage1Old = pipelineTrace.get(4);
        assertEquals(stage1OldName, stepStage1Old.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));

        //----
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(6);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(6, spans.size());

        final TraceSpan stage1 = spans.get(2);
        final String stage1Name = stage1.getMeta().get(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage1Name != null && !stage1Name.isEmpty());

        final TraceSpan stepStage1 = spans.get(3);
        assertEquals(stage1Name, stepStage1.getMeta().get(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));

        final TraceSpan stage2 = spans.get(4);
        final String stage2Name = stage2.getMeta().get(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage2Name != null && !stage2Name.isEmpty());

        final TraceSpan stepStage2 = spans.get(5);
        assertEquals(stage2Name, stepStage2.getMeta().get(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));
    }

    @Test
    public void testIntegrationPipelineQueueTimeOnStages() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationQueueTimeOnStages");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineQueueOnStages.txt"),
                "UTF-8"
        );
        job.setDefinition(new CpsFlowDefinition(definition, true));
        // schedule build and wait for it to get queued
        new Thread(() -> {
            try {
                job.scheduleBuild2(0).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(10000);
        final DumbSlave worker = jenkinsRule.createOnlineSlave(Label.get("testStage"));

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        //TODO Remove Java Tracer
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        //TODO Remove Java Tracer
        final DDSpan buildSpanOld = buildTrace.get(0);
        assertEquals(0L, buildSpanOld.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("master", buildSpanOld.getTag(CITags.NODE_NAME));
        assertEquals("[\"master\"]", buildSpanOld.getTag(CITags.NODE_LABELS));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(5, pipelineTrace.size());

        //TODO Remove Java Tracer
        final DDSpan runStagesOld = pipelineTrace.get(0);
        assertEquals(0L, runStagesOld.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("master", runStagesOld.getTag(CITags.NODE_NAME));
        assertEquals("[\"master\"]", runStagesOld.getTag(CITags.NODE_LABELS));

        //TODO Remove Java Tracer
        final DDSpan stage2Old = pipelineTrace.get(1);
        long stage2OldQueueTime = (long) stage2Old.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage2OldQueueTime > 0L);
        assertTrue(stage2OldQueueTime > TimeUnit.NANOSECONDS.toSeconds(stage2Old.getDurationNano()));
        assertTrue(stage2Old.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), stage2Old.getTag(CITags.NODE_NAME));
        assertTrue(((String)stage2Old.getTag(CITags.NODE_LABELS)).contains("testStage"));

        //TODO Remove Java Tracer
        final DDSpan stepStage2Old = pipelineTrace.get(2);
        assertEquals(0L, stepStage2Old.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stepStage2Old.getTag(CITags.NODE_NAME));
        assertTrue(((String)stepStage2Old.getTag(CITags.NODE_LABELS)).contains("testStage"));

        //TODO Remove Java Tracer
        final DDSpan stage1Old = pipelineTrace.get(3);
        long stage1OldQueueTime = (long) stage1Old.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage1OldQueueTime > 0L);
        assertTrue(stage1OldQueueTime > TimeUnit.NANOSECONDS.toSeconds(stage1Old.getDurationNano()));
        assertTrue(stage1Old.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), stage1Old.getTag(CITags.NODE_NAME));
        assertTrue(((String) stage1Old.getTag(CITags.NODE_LABELS)).contains("testStage"));

        //TODO Remove Java Tracer
        final DDSpan stepStage1Old = pipelineTrace.get(4);
        assertEquals(0L, stepStage1Old.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stepStage1Old.getTag(CITags.NODE_NAME));
        assertTrue(((String) stepStage1Old.getTag(CITags.NODE_LABELS)).contains("testStage"));

        //---
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(6);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(6, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertEquals(Double.valueOf(0), buildSpan.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals("master", buildSpan.getMeta().get(CITags.NODE_NAME));
        assertEquals("[\"master\"]", buildSpan.getMeta().get(CITags.NODE_LABELS));

        final TraceSpan runStages = spans.get(1);
        assertEquals(Double.valueOf(0), runStages.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals("master", runStages.getMeta().get(CITags.NODE_NAME));
        assertEquals("[\"master\"]", runStages.getMeta().get(CITags.NODE_LABELS));

        final TraceSpan stage1 = spans.get(2);
        final Double stage1QueueTime = stage1.getMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage1QueueTime > 0L);
        assertTrue(stage1QueueTime > TimeUnit.NANOSECONDS.toSeconds(stage1.getDurationNano()));
        assertTrue(stage1.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), stage1.getMeta().get(CITags.NODE_NAME));
        assertTrue(stage1.getMeta().get(CITags.NODE_LABELS).contains("testStage"));

        final TraceSpan stepStage1 = spans.get(3);
        assertEquals(Double.valueOf(0), stepStage1.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stepStage1.getMeta().get(CITags.NODE_NAME));
        assertTrue(stepStage1.getMeta().get(CITags.NODE_LABELS).contains("testStage"));

        final TraceSpan stage2 = spans.get(4);
        final Double stage2QueueTime = stage2.getMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage2QueueTime > 0L);
        assertTrue(stage2QueueTime > TimeUnit.NANOSECONDS.toSeconds(stage2.getDurationNano()));
        assertTrue(stage2.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), stage2.getMeta().get(CITags.NODE_NAME));
        assertTrue(stage2.getMeta().get(CITags.NODE_LABELS).contains("testStage"));

        final TraceSpan stepStage2 = spans.get(5);
        assertEquals(Double.valueOf(0), stepStage2.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stepStage2.getMeta().get(CITags.NODE_NAME));
        assertTrue(stepStage2.getMeta().get(CITags.NODE_LABELS).contains("testStage"));
    }


    @Test
    public void testIntegrationPipelineQueueTimeOnPipeline() throws Exception {
        final EnvironmentVariablesNodeProperty envProps = new EnvironmentVariablesNodeProperty();
        EnvVars env = envProps.getEnvVars();
        env.put("NODE_NAME", "testPipeline");
        jenkinsRule.jenkins.getGlobalNodeProperties().add(envProps);
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationQueueTimeOnPipeline");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineQueueOnPipeline.txt"),
                "UTF-8"
        );
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

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        //TODO Remove Java Tracer
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpanOld = buildTrace.get(0);
        long queueTimeOld = (long) buildSpanOld.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(queueTimeOld > 0L);
        assertTrue(queueTimeOld > TimeUnit.NANOSECONDS.toSeconds(buildSpanOld.getDurationNano()));
        assertTrue(buildSpanOld.getDurationNano() > 1L);

        //TODO Remove Java Tracer
        assertEquals(worker.getNodeName(), buildSpanOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) buildSpanOld.getTag(CITags.NODE_LABELS)).contains("testPipeline"));
        assertEquals("none",buildSpanOld.getTag(CITags._DD_HOSTNAME));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(2, pipelineTrace.size());
        //TODO Remove Java Tracer
        final DDSpan stageOld = pipelineTrace.get(0);
        assertEquals(0L, stageOld.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stageOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) stageOld.getTag(CITags.NODE_LABELS)).contains("testPipeline"));
        assertEquals("none",stageOld.getTag(CITags._DD_HOSTNAME));

        //TODO Remove Java Tracer
        final DDSpan stepOld = pipelineTrace.get(1);
        assertEquals(0L, stepOld.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stepOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) stepOld.getTag(CITags.NODE_LABELS)).contains("testPipeline"));
        assertEquals("none",stepOld.getTag(CITags._DD_HOSTNAME));

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(3);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(3, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        final Double queueTime = buildSpan.getMetrics().get(CITags.QUEUE_TIME);
        assertTrue(queueTime > 0L);
        assertTrue(queueTime > TimeUnit.NANOSECONDS.toSeconds(buildSpan.getDurationNano()));
        assertTrue(buildSpan.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), buildSpan.getMeta().get(CITags.NODE_NAME));
        assertTrue(buildSpan.getMeta().get(CITags.NODE_LABELS).contains("testPipeline"));
        assertEquals("none",buildSpan.getMeta().get(CITags._DD_HOSTNAME));

        final TraceSpan stage = spans.get(1);
        assertEquals(Double.valueOf(0), stage.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stage.getMeta().get(CITags.NODE_NAME));
        assertTrue(stage.getMeta().get(CITags.NODE_LABELS).contains("testPipeline"));
        assertEquals("none",stage.getMeta().get(CITags._DD_HOSTNAME));

        final TraceSpan step = spans.get(2);
        assertEquals(Double.valueOf(0), step.getMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), step.getMeta().get(CITags.NODE_NAME));
        assertTrue(step.getMeta().get(CITags.NODE_LABELS).contains("testPipeline"));
        assertEquals("none",step.getMeta().get(CITags._DD_HOSTNAME));
    }

    @Test
    public void testIntegrationNoFailureTag() throws Exception {
        jenkinsRule.createOnlineSlave(new LabelAtom("windows"));
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationSuccess");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineSuccess.txt"),
                "UTF-8"
        );
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

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        //TODO Remove Java Tracer
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final String buildPrefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();
        final DDSpan buildSpanOld = buildTrace.get(0);
        assertEquals("jenkins.build", buildSpanOld.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, buildSpanOld.getServiceName());
        assertEquals(ORIGIN_CIAPP_PIPELINE, buildSpanOld.getTag(CITags._DD_ORIGIN));
        assertEquals("pipelineIntegrationSuccess", buildSpanOld.getResourceName());
        assertEquals("ci", buildSpanOld.getType());
        assertEquals("anonymous", buildSpanOld.getTag(CITags.USER_NAME));
        assertEquals("jenkins", buildSpanOld.getTag(CITags.CI_PROVIDER_NAME));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", buildSpanOld.getTag(buildPrefix + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", buildSpanOld.getTag(buildPrefix + CITags._NAME));
        assertEquals("1", buildSpanOld.getTag(buildPrefix + CITags._NUMBER));
        assertEquals("success", buildSpanOld.getTag(buildPrefix + CITags._RESULT));
        assertEquals("success", buildSpanOld.getTag(CITags.STATUS));
        assertNotNull(buildSpanOld.getTag(buildPrefix + CITags._URL));
        assertNotNull(buildSpanOld.getTag(CITags.NODE_NAME));
        assertNotNull(buildSpanOld.getTag(CITags.NODE_LABELS));
        assertNull(buildSpanOld.getTag(CITags._DD_HOSTNAME));
        assertEquals("success", buildSpanOld.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", buildSpanOld.getTag(CITags.JENKINS_TAG));
        assertEquals(false, buildSpanOld.getTag(CITags._DD_CI_INTERNAL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpanOld.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpanOld.getTag(CITags._DD_CI_LEVEL));
        assertNotNull(buildSpanOld.getTag(CITags._DD_CI_STAGES));
        assertTrue(((String) buildSpanOld.getTag(CITags._DD_CI_STAGES)).contains("{\"name\":\"test\",\"duration\""));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(2, pipelineTrace.size());

        //TODO Remove Java Tracer
        final String stagePrefix = BuildPipelineNode.NodeType.STAGE.getTagName();
        final DDSpan stageSpanOld = pipelineTrace.get(0);
        assertEquals("jenkins.stage", stageSpanOld.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, stageSpanOld.getServiceName());
        assertEquals(ORIGIN_CIAPP_PIPELINE, stageSpanOld.getTag(CITags._DD_ORIGIN));
        assertEquals("test", stageSpanOld.getResourceName());
        assertEquals("ci", stageSpanOld.getType());
        assertEquals("test", stageSpanOld.getTag(stagePrefix + CITags._NAME));
        assertEquals("success", stageSpanOld.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stageSpanOld.getTag(CITags.CI_PROVIDER_NAME));
        assertNotNull(stageSpanOld.getTag(stagePrefix + CITags._URL));
        assertNotNull(stageSpanOld.getTag(CITags.NODE_NAME));
        assertNotNull(stageSpanOld.getTag(CITags.NODE_LABELS));
        assertNull(stageSpanOld.getTag(CITags._DD_HOSTNAME));
        assertEquals(false, stageSpanOld.getTag(CITags._DD_CI_INTERNAL));
        assertEquals("4", stageSpanOld.getTag(stagePrefix + CITags._NUMBER));
        assertEquals(BuildPipelineNode.NodeType.STAGE.getBuildLevel(), stageSpanOld.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.STAGE.getBuildLevel(), stageSpanOld.getTag(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stageSpanOld.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stageSpanOld.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME));
        assertNotNull(stageSpanOld.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        //TODO Remove Java Tracer
        final String stepPrefix = BuildPipelineNode.NodeType.STEP.getTagName();
        final DDSpan stepAtomSpanOld = pipelineTrace.get(1);
        assertEquals("jenkins.step", stepAtomSpanOld.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, stepAtomSpanOld.getServiceName());
        assertEquals(ORIGIN_CIAPP_PIPELINE, stepAtomSpanOld.getTag(CITags._DD_ORIGIN));
        assertEquals("Print Message", stepAtomSpanOld.getResourceName());
        assertEquals("ci", stepAtomSpanOld.getType());
        assertEquals("Print Message", stepAtomSpanOld.getTag(stepPrefix + CITags._NAME));
        assertEquals("success", stepAtomSpanOld.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stepAtomSpanOld.getTag(CITags.CI_PROVIDER_NAME));
        assertEquals("hello", stepAtomSpanOld.getTag("jenkins.step.args.message"));
        assertNotNull(stepAtomSpanOld.getTag(stepPrefix + CITags._URL));
        assertNotNull(stepAtomSpanOld.getTag(stepPrefix + CITags._URL));
        assertNotNull(stepAtomSpanOld.getTag(CITags.NODE_NAME));
        assertNotNull(stepAtomSpanOld.getTag(CITags.NODE_LABELS));
        assertNull(stepAtomSpanOld.getTag(CITags._DD_HOSTNAME));
        assertEquals(false, stepAtomSpanOld.getTag(CITags._DD_CI_INTERNAL));
        assertEquals("5", stepAtomSpanOld.getTag(stepPrefix + CITags._NUMBER));
        assertEquals(BuildPipelineNode.NodeType.STEP.getBuildLevel(), stepAtomSpanOld.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.STEP.getBuildLevel(), stepAtomSpanOld.getTag(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stepAtomSpanOld.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stepAtomSpanOld.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME));
        assertEquals("test", stepAtomSpanOld.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));
        assertNotNull(stepAtomSpanOld.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        //----
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(3);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
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
        assertNull(buildSpanMeta.get(CITags._DD_HOSTNAME));
        assertEquals("success", buildSpanMeta.get(CITags.JENKINS_RESULT));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", buildSpanMeta.get(CITags.JENKINS_TAG));
        assertEquals("false", buildSpanMeta.get(CITags._DD_CI_INTERNAL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpanMeta.get(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpanMeta.get(CITags._DD_CI_LEVEL));
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
        assertNull(stageSpanMeta.get(CITags._DD_HOSTNAME));
        assertEquals("false", stageSpanMeta.get(CITags._DD_CI_INTERNAL));
        assertEquals("4", stageSpanMeta.get(stagePrefix + CITags._NUMBER));
        assertEquals(BuildPipelineNode.NodeType.STAGE.getBuildLevel(), stageSpanMeta.get(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.STAGE.getBuildLevel(), stageSpanMeta.get(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stageSpanMeta.get(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stageSpanMeta.get(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME));
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
        assertNull(stepSpanMeta.get(CITags._DD_HOSTNAME));
        assertEquals("false", stepSpanMeta.get(CITags._DD_CI_INTERNAL));
        assertEquals("5", stepSpanMeta.get(stepPrefix + CITags._NUMBER));
        assertEquals(BuildPipelineNode.NodeType.STEP.getBuildLevel(), stepSpanMeta.get(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.STEP.getBuildLevel(), stepSpanMeta.get(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stepSpanMeta.get(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stepSpanMeta.get(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME));
        assertEquals("test", stepSpanMeta.get(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));
        assertNotNull(stepSpan.getMetrics().get(CITags.QUEUE_TIME));

        assertCleanupActions(run);
    }

    @Test
    public void testIntegrationPipelineSkippedLogic() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration-SkippedLogic");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineSkippedLogic.txt"),
                "UTF-8"
        );
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        //TODO Remove Java Tracer
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(1, pipelineTrace.size());

        //TODO Remove Java Tracer
        final DDSpan stageOld = pipelineTrace.get(0);
        assertEquals("Stage", stageOld.getResourceName());
        assertEquals("skipped", stageOld.getTag(CITags.STATUS));

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(2);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(2, spans.size());

        final TraceSpan stage = spans.get(1);
        assertEquals("Stage", stage.getResourceName());
        assertEquals("skipped", stage.getMeta().get(CITags.STATUS));
    }


    @Test
    public void testIntegrationTracesDisabled() throws Exception{
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(false);

        jenkinsRule.createOnlineSlave(new LabelAtom("windows"));
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationSuccess-notraces");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineSuccess.txt"),
                "UTF-8"
        );
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

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(0);
        assertEquals(0, tracerWriter.size());

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(0);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
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
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineComplexStages01.txt"),
                "UTF-8"
        );
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

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(7);
        assertEquals(7, tracerWriter.size());

        //TODO Remove Java Tracer
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        //TODO Remove Java Tracer
        final DDSpan buildSpanOld = buildTrace.get(0);
        assertEquals(worker03.getNodeName(), buildSpanOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) buildSpanOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace01 = tracerWriter.get(1);
        assertEquals(5, pipelineTrace01.size());

        //TODO Remove Java Tracer
        final DDSpan prepareBlockOld = pipelineTrace01.get(0);
        assertEquals("Prepare", prepareBlockOld.getResourceName());
        assertEquals(worker03.getNodeName(), prepareBlockOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) prepareBlockOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final DDSpan prepareStage01Old = pipelineTrace01.get(1);
        assertNodeNameParallelBlockOld(prepareStage01Old, worker01, worker02);

        //TODO Remove Java Tracer
        final DDSpan prepareStep01Old = pipelineTrace01.get(2);
        assertEquals(prepareStage01Old.getTag(CITags.NODE_NAME), prepareStep01Old.getTag(CITags.NODE_NAME));
        assertEquals(prepareStage01Old.getTag(CITags.NODE_LABELS), prepareStep01Old.getTag(CITags.NODE_LABELS));

        //TODO Remove Java Tracer
        final DDSpan prepareStage02Old = pipelineTrace01.get(3);
        assertNodeNameParallelBlockOld(prepareStage02Old, worker01, worker02);

        //TODO Remove Java Tracer
        final DDSpan prepareStep02Old = pipelineTrace01.get(4);
        assertEquals(prepareStage02Old.getTag(CITags.NODE_NAME), prepareStep02Old.getTag(CITags.NODE_NAME));
        assertEquals(prepareStage02Old.getTag(CITags.NODE_LABELS), prepareStep02Old.getTag(CITags.NODE_LABELS));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace02 = tracerWriter.get(2);
        assertEquals(2, pipelineTrace02.size());

        //TODO Remove Java Tracer
        final DDSpan installStageOld = pipelineTrace02.get(0);
        assertEquals("Install", installStageOld.getResourceName());
        assertEquals(worker03.getNodeName(), installStageOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) installStageOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final DDSpan installStepOld = pipelineTrace02.get(1);
        assertEquals(worker03.getNodeName(), installStepOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) installStepOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace03 = tracerWriter.get(3);
        assertEquals(2, pipelineTrace03.size());

        //TODO Remove Java Tracer
        final DDSpan bumpVersionStageOld = pipelineTrace03.get(0);
        assertEquals("Bump version", bumpVersionStageOld.getResourceName());
        assertEquals(worker03.getNodeName(), bumpVersionStageOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) bumpVersionStageOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final DDSpan bumpVersionStepOld = pipelineTrace03.get(1);
        assertEquals(worker03.getNodeName(), bumpVersionStepOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) bumpVersionStepOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace04 = tracerWriter.get(4);
        assertEquals(2, pipelineTrace04.size());

        //TODO Remove Java Tracer
        final DDSpan buildStageOld = pipelineTrace04.get(0);
        assertEquals("Build", buildStageOld.getResourceName());
        assertEquals(worker03.getNodeName(), buildStageOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) buildStageOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final DDSpan buildStepOld = pipelineTrace04.get(1);
        assertEquals(worker03.getNodeName(), buildStepOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) buildStepOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace05 = tracerWriter.get(5);
        assertEquals(5, pipelineTrace05.size());

        //TODO Remove Java Tracer
        final DDSpan validateBlockOld = pipelineTrace05.get(0);
        assertEquals("Validate", validateBlockOld.getResourceName());
        assertEquals(worker03.getNodeName(), validateBlockOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) validateBlockOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final DDSpan validateStage01Old = pipelineTrace05.get(1);
        assertNodeNameParallelBlockOld(validateStage01Old, worker01, worker02);

        //TODO Remove Java Tracer
        final DDSpan validateStep01Old = pipelineTrace05.get(2);
        assertEquals(validateStage01Old.getTag(CITags.NODE_NAME), validateStep01Old.getTag(CITags.NODE_NAME));
        assertEquals(validateStage01Old.getTag(CITags.NODE_LABELS), validateStep01Old.getTag(CITags.NODE_LABELS));

        //TODO Remove Java Tracer
        final DDSpan validateStage02Old = pipelineTrace05.get(3);
        assertNodeNameParallelBlockOld(validateStage02Old, worker01, worker02);

        //TODO Remove Java Tracer
        final DDSpan validateStep02Old = pipelineTrace05.get(4);
        assertEquals(validateStage02Old.getTag(CITags.NODE_NAME), validateStep02Old.getTag(CITags.NODE_NAME));
        assertEquals(validateStage02Old.getTag(CITags.NODE_LABELS), validateStep02Old.getTag(CITags.NODE_LABELS));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace06 = tracerWriter.get(6);
        assertEquals(2, pipelineTrace06.size());

        //TODO Remove Java Tracer
        final DDSpan ciStageOld = pipelineTrace06.get(0);
        assertEquals("CI", ciStageOld.getResourceName());
        assertEquals(worker03.getNodeName(), ciStageOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) ciStageOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //TODO Remove Java Tracer
        final DDSpan ciStepOld = pipelineTrace06.get(1);
        assertEquals(worker03.getNodeName(), ciStepOld.getTag(CITags.NODE_NAME));
        assertTrue(((String) ciStepOld.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(19);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(19, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertEquals(worker03.getNodeName(), buildSpan.getMeta().get(CITags.NODE_NAME));
        assertTrue(buildSpan.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

        final TraceSpan prepareBlock = spans.get(1);
        assertEquals("Prepare", prepareBlock.getResourceName());
        assertEquals(worker03.getNodeName(), prepareBlock.getMeta().get(CITags.NODE_NAME));
        assertTrue(prepareBlock.getMeta().get(CITags.NODE_LABELS).contains(worker03.getNodeName()));

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
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineGlobalTags.txt"),
                "UTF-8"
        );
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        //TODO Remove Java Tracer
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpanOld = buildTrace.get(0);
        assertEquals("value", buildSpanOld.getTag("global_tag"));
        assertEquals("value", buildSpanOld.getTag("global_job_tag"));
        assertEquals("pipeline_tag_v2", buildSpanOld.getTag("pipeline_tag"));
        assertEquals("pipeline_tag", buildSpanOld.getTag("pipeline_tag_v2"));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(2, pipelineTrace.size());
        final DDSpan stageSpanOld = pipelineTrace.get(0);
        assertEquals("value", stageSpanOld.getTag("global_tag"));
        assertEquals("value", stageSpanOld.getTag("global_job_tag"));
        assertEquals("pipeline_tag_v2", stageSpanOld.getTag("pipeline_tag"));
        assertEquals("pipeline_tag", stageSpanOld.getTag("pipeline_tag_v2"));

        //TODO Remove Java Tracer
        final DDSpan stepSpanOld = pipelineTrace.get(1);
        assertEquals("value", stepSpanOld.getTag("global_tag"));
        assertEquals("value", stepSpanOld.getTag("global_job_tag"));
        assertEquals("pipeline_tag_v2", stepSpanOld.getTag("pipeline_tag"));
        assertEquals("pipeline_tag", stepSpanOld.getTag("pipeline_tag_v2"));

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(3);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
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
    public void testErrorPropagationOnStages() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegration-errorPropagationStages");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineErrorOnStages.txt"),
                "UTF-8"
        );
        job.setDefinition(new CpsFlowDefinition(definition, true));
        job.scheduleBuild2(0).get();

        //TODO Remove Java Tracer
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        //TODO Remove Java Tracer
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpanOld = buildTrace.get(0);
        assertEquals("error", buildSpanOld.getTag(CITags.STATUS));

        //TODO Remove Java Tracer
        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(3, pipelineTrace.size());

        //TODO Remove Java Tracer
        final DDSpan stageSpanOld = pipelineTrace.get(0);
        assertEquals("error", stageSpanOld.getTag(CITags.STATUS));

        //TODO Remove Java Tracer
        final DDSpan stepSpanOld = pipelineTrace.get(1);
        assertEquals("error", stepSpanOld.getTag(CITags.STATUS));

        //TODO Remove Java Tracer
        final DDSpan step2SpanOld = pipelineTrace.get(2);
        assertEquals("error", step2SpanOld.getTag(CITags.STATUS));

        //--
        final FakeAgentHttpClient agentHttpClient = clientStub.agentHttpClient();
        agentHttpClient.waitForTraces(4);
        final List<TraceSpan> spans = agentHttpClient.getSpans();
        assertEquals(4, spans.size());

        final TraceSpan buildSpan = spans.get(0);
        assertEquals("error", buildSpan.getMeta().get(CITags.STATUS));

        final TraceSpan stageSpan = spans.get(1);
        assertEquals("error", stageSpan.getMeta().get(CITags.STATUS));

        final TraceSpan stepSpan = spans.get(2);
        assertEquals("error", stepSpan.getMeta().get(CITags.STATUS));

        final TraceSpan step2Span = spans.get(3);
        assertEquals("error", step2Span.getMeta().get(CITags.STATUS));
    }

    //TODO Remove Java Tracer
    private void assertNodeNameParallelBlockOld(DDSpan stageSpan, DumbSlave worker01, DumbSlave worker02) {
        switch ((String)stageSpan.getResourceName()){
            case "Prepare01":
            case "Validate01":
                assertEquals(worker01.getNodeName(), stageSpan.getTag(CITags.NODE_NAME));
                assertTrue(((String) stageSpan.getTag(CITags.NODE_LABELS)).contains(worker01.getNodeName()));
                break;
            case "Prepare02":
            case "Validate02":
                assertEquals(worker02.getNodeName(), stageSpan.getTag(CITags.NODE_NAME));
                assertTrue(((String) stageSpan.getTag(CITags.NODE_LABELS)).contains(worker02.getNodeName()));
                break;
        }
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
}
