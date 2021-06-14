package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
import java.util.concurrent.TimeUnit;

public class DatadogGraphListenerTest {

    private static final String SAMPLE_SERVICE_NAME = "sampleServiceName";

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private DatadogGraphListener listener;
    private DatadogClientStub clientStub;

    @Before
    public void beforeEach() throws IOException {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setCollectBuildTraces(true);
        cfg.setTraceServiceName(SAMPLE_SERVICE_NAME);
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

        //Traces
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(3);
        assertEquals(3, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final List<DDSpan> stage01Trace = tracerWriter.get(1);
        assertEquals(2, stage01Trace.size());

        final List<DDSpan> stage02Trace = tracerWriter.get(2);
        assertEquals(13, stage02Trace.size());
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

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpan = buildTrace.get(0);
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

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpan = buildTrace.get(0);
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

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(3);
        assertEquals(3, tracerWriter.size());
        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", buildSpan.getTag(CITags.GIT_COMMIT_SHA));

        final List<DDSpan> stage1Chain = tracerWriter.get(1);
        assertEquals(2, stage1Chain.size());
        final DDSpan stage1 = stage1Chain.get(0);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", stage1.getTag(CITags.GIT_COMMIT_SHA));
        final DDSpan step1 = stage1Chain.get(1);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", step1.getTag(CITags.GIT_COMMIT_SHA));

        final List<DDSpan> stage2Chain = tracerWriter.get(2);
        assertEquals(2, stage2Chain.size());
        final DDSpan stage2 = stage2Chain.get(0);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", stage2.getTag(CITags.GIT_COMMIT_SHA));
        final DDSpan step2 = stage2Chain.get(1);
        assertEquals("401d997a6eede777602669ccaec059755c98161f", step2.getTag(CITags.GIT_COMMIT_SHA));
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

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(5, pipelineTrace.size());

        final DDSpan stage2 = pipelineTrace.get(1);
        final String stage2Name = (String) stage2.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage2Name != null && !stage2Name.isEmpty());

        final DDSpan stepStage2 = pipelineTrace.get(2);
        assertEquals(stage2Name, stepStage2.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));

        final DDSpan stage1 = pipelineTrace.get(3);
        final String stage1Name = (String) stage1.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage1Name != null && !stage1Name.isEmpty());

        final DDSpan stepStage1 = pipelineTrace.get(4);
        assertEquals(stage1Name, stepStage1.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));
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


        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals(0L, buildSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("master", buildSpan.getTag(CITags.NODE_NAME));
        assertEquals("[\"master\"]", buildSpan.getTag(CITags.NODE_LABELS));

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(5, pipelineTrace.size());

        final DDSpan runStages = pipelineTrace.get(0);
        assertEquals(0L, runStages.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("master", runStages.getTag(CITags.NODE_NAME));
        assertEquals("[\"master\"]", runStages.getTag(CITags.NODE_LABELS));

        final DDSpan stage2 = pipelineTrace.get(1);
        long stage2QueueTime = (long) stage2.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage2QueueTime > 0L);
        assertTrue(stage2QueueTime > TimeUnit.NANOSECONDS.toSeconds(stage2.getDurationNano()));
        assertTrue(stage2.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), stage2.getTag(CITags.NODE_NAME));
        assertTrue(((String)stage2.getTag(CITags.NODE_LABELS)).contains("testStage"));

        final DDSpan stepStage2 = pipelineTrace.get(2);
        assertEquals(0L, stepStage2.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stepStage2.getTag(CITags.NODE_NAME));
        assertTrue(((String)stepStage2.getTag(CITags.NODE_LABELS)).contains("testStage"));

        final DDSpan stage1 = pipelineTrace.get(3);
        long stage1QueueTime = (long) stage1.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage1QueueTime > 0L);
        assertTrue(stage1QueueTime > TimeUnit.NANOSECONDS.toSeconds(stage1.getDurationNano()));
        assertTrue(stage1.getDurationNano() > 1L);
        assertEquals(worker.getNodeName(), stage1.getTag(CITags.NODE_NAME));
        assertTrue(((String) stage1.getTag(CITags.NODE_LABELS)).contains("testStage"));

        final DDSpan stepStage1 = pipelineTrace.get(4);
        assertEquals(0L, stepStage1.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stepStage1.getTag(CITags.NODE_NAME));
        assertTrue(((String) stepStage1.getTag(CITags.NODE_LABELS)).contains("testStage"));
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

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpan = buildTrace.get(0);
        long queueTime = (long) buildSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(queueTime > 0L);
        assertTrue(queueTime > TimeUnit.NANOSECONDS.toSeconds(buildSpan.getDurationNano()));
        assertTrue(buildSpan.getDurationNano() > 1L);


        assertEquals(worker.getNodeName(), buildSpan.getTag(CITags.NODE_NAME));
        assertTrue(((String) buildSpan.getTag(CITags.NODE_LABELS)).contains("testPipeline"));
        assertEquals("none",buildSpan.getTag(CITags._DD_HOSTNAME));

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(2, pipelineTrace.size());

        final DDSpan stage = pipelineTrace.get(0);
        assertEquals(0L, stage.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), stage.getTag(CITags.NODE_NAME));
        assertTrue(((String) stage.getTag(CITags.NODE_LABELS)).contains("testPipeline"));
        assertEquals("none",stage.getTag(CITags._DD_HOSTNAME));

        final DDSpan step = pipelineTrace.get(1);
        assertEquals(0L, step.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals(worker.getNodeName(), step.getTag(CITags.NODE_NAME));
        assertTrue(((String) step.getTag(CITags.NODE_LABELS)).contains("testPipeline"));
        assertEquals("none",step.getTag(CITags._DD_HOSTNAME));
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
        job.scheduleBuild2(0).get();
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

        //Traces
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final String buildPrefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();
        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("jenkins.build", buildSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, buildSpan.getServiceName());
        assertEquals("pipelineIntegrationSuccess", buildSpan.getResourceName());
        assertEquals("ci", buildSpan.getType());
        assertEquals("anonymous", buildSpan.getTag(CITags.USER_NAME));
        assertEquals("jenkins", buildSpan.getTag(CITags.CI_PROVIDER_NAME));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", buildSpan.getTag(buildPrefix + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", buildSpan.getTag(buildPrefix + CITags._NAME));
        assertEquals("1", buildSpan.getTag(buildPrefix + CITags._NUMBER));
        assertEquals("success", buildSpan.getTag(buildPrefix + CITags._RESULT));
        assertEquals("success", buildSpan.getTag(CITags.STATUS));
        assertNotNull(buildSpan.getTag(buildPrefix + CITags._URL));
        assertNotNull(buildSpan.getTag(CITags.NODE_NAME));
        assertNotNull(buildSpan.getTag(CITags.NODE_LABELS));
        assertNull(buildSpan.getTag(CITags._DD_HOSTNAME));
        assertEquals("success", buildSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", buildSpan.getTag(CITags.JENKINS_TAG));
        assertEquals(false, buildSpan.getTag(CITags._DD_CI_INTERNAL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpan.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpan.getTag(CITags._DD_CI_LEVEL));
        assertNotNull(buildSpan.getTag(CITags._DD_CI_STAGES));
        assertTrue(((String) buildSpan.getTag(CITags._DD_CI_STAGES)).contains("{\"name\":\"test\",\"duration\""));

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(2, pipelineTrace.size());

        final String stagePrefix = BuildPipelineNode.NodeType.STAGE.getTagName();
        final DDSpan stageSpan = pipelineTrace.get(0);
        assertEquals("jenkins.stage", stageSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, stageSpan.getServiceName());
        assertEquals("test", stageSpan.getResourceName());
        assertEquals("ci", stageSpan.getType());
        assertEquals("test", stageSpan.getTag(stagePrefix + CITags._NAME));
        assertEquals("success", stageSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stageSpan.getTag(CITags.CI_PROVIDER_NAME));
        assertNotNull(stageSpan.getTag(stagePrefix + CITags._URL));
        assertNotNull(stageSpan.getTag(CITags.NODE_NAME));
        assertNotNull(stageSpan.getTag(CITags.NODE_LABELS));
        assertNull(stageSpan.getTag(CITags._DD_HOSTNAME));
        assertEquals(false, stageSpan.getTag(CITags._DD_CI_INTERNAL));
        assertEquals("4", stageSpan.getTag(stagePrefix + CITags._NUMBER));
        assertEquals(BuildPipelineNode.NodeType.STAGE.getBuildLevel(), stageSpan.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.STAGE.getBuildLevel(), stageSpan.getTag(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stageSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stageSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME));
        assertNotNull(stageSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final String stepPrefix = BuildPipelineNode.NodeType.STEP.getTagName();
        final DDSpan stepAtomSpan = pipelineTrace.get(1);
        assertEquals("jenkins.step", stepAtomSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, stepAtomSpan.getServiceName());
        assertEquals("Print Message", stepAtomSpan.getResourceName());
        assertEquals("ci", stepAtomSpan.getType());
        assertEquals("Print Message", stepAtomSpan.getTag(stepPrefix + CITags._NAME));
        assertEquals("success", stepAtomSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stepAtomSpan.getTag(CITags.CI_PROVIDER_NAME));
        assertEquals("hello", stepAtomSpan.getTag("jenkins.step.args.message"));
        assertNotNull(stepAtomSpan.getTag(stepPrefix + CITags._URL));
        assertNotNull(stepAtomSpan.getTag(stepPrefix + CITags._URL));
        assertNotNull(stepAtomSpan.getTag(CITags.NODE_NAME));
        assertNotNull(stepAtomSpan.getTag(CITags.NODE_LABELS));
        assertNull(stepAtomSpan.getTag(CITags._DD_HOSTNAME));
        assertEquals(false, stepAtomSpan.getTag(CITags._DD_CI_INTERNAL));
        assertEquals("5", stepAtomSpan.getTag(stepPrefix + CITags._NUMBER));
        assertEquals(BuildPipelineNode.NodeType.STEP.getBuildLevel(), stepAtomSpan.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.STEP.getBuildLevel(), stepAtomSpan.getTag(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stepAtomSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stepAtomSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME));
        assertEquals("test", stepAtomSpan.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));
        assertNotNull(stepAtomSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME));
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

        //Traces
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(1, pipelineTrace.size());

        final DDSpan stage = pipelineTrace.get(0);
        assertEquals("Stage", stage.getResourceName());
        assertEquals("skipped", stage.getTag(CITags.STATUS));
    }


    @Test
    public void testIntegrationTracesDisabled() throws Exception{
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setCollectBuildTraces(false);

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

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(0);
        assertEquals(0, tracerWriter.size());
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

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(7);
        assertEquals(7, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals(worker03.getNodeName(), buildSpan.getTag(CITags.NODE_NAME));
        assertTrue(((String) buildSpan.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final List<DDSpan> pipelineTrace01 = tracerWriter.get(1);
        assertEquals(5, pipelineTrace01.size());

        final DDSpan prepareBlock = pipelineTrace01.get(0);
        assertEquals("Prepare", prepareBlock.getResourceName());
        assertEquals(worker03.getNodeName(), prepareBlock.getTag(CITags.NODE_NAME));
        assertTrue(((String) prepareBlock.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final DDSpan prepareStage01 = pipelineTrace01.get(1);
        assertNodeNameParallelBlock(prepareStage01, worker01, worker02);

        final DDSpan prepareStep01 = pipelineTrace01.get(2);
        assertEquals(prepareStage01.getTag(CITags.NODE_NAME), prepareStep01.getTag(CITags.NODE_NAME));
        assertEquals(prepareStage01.getTag(CITags.NODE_LABELS), prepareStep01.getTag(CITags.NODE_LABELS));

        final DDSpan prepareStage02 = pipelineTrace01.get(3);
        assertNodeNameParallelBlock(prepareStage02, worker01, worker02);

        final DDSpan prepareStep02 = pipelineTrace01.get(4);
        assertEquals(prepareStage02.getTag(CITags.NODE_NAME), prepareStep02.getTag(CITags.NODE_NAME));
        assertEquals(prepareStage02.getTag(CITags.NODE_LABELS), prepareStep02.getTag(CITags.NODE_LABELS));

        final List<DDSpan> pipelineTrace02 = tracerWriter.get(2);
        assertEquals(2, pipelineTrace02.size());

        final DDSpan installStage = pipelineTrace02.get(0);
        assertEquals("Install", installStage.getResourceName());
        assertEquals(worker03.getNodeName(), installStage.getTag(CITags.NODE_NAME));
        assertTrue(((String) installStage.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final DDSpan installStep = pipelineTrace02.get(1);
        assertEquals(worker03.getNodeName(), installStep.getTag(CITags.NODE_NAME));
        assertTrue(((String) installStep.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final List<DDSpan> pipelineTrace03 = tracerWriter.get(3);
        assertEquals(2, pipelineTrace03.size());

        final DDSpan bumpVersionStage = pipelineTrace03.get(0);
        assertEquals("Bump version", bumpVersionStage.getResourceName());
        assertEquals(worker03.getNodeName(), bumpVersionStage.getTag(CITags.NODE_NAME));
        assertTrue(((String) bumpVersionStage.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final DDSpan bumpVersionStep = pipelineTrace03.get(1);
        assertEquals(worker03.getNodeName(), bumpVersionStep.getTag(CITags.NODE_NAME));
        assertTrue(((String) bumpVersionStep.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final List<DDSpan> pipelineTrace04 = tracerWriter.get(4);
        assertEquals(2, pipelineTrace04.size());

        final DDSpan buildStage = pipelineTrace04.get(0);
        assertEquals("Build", buildStage.getResourceName());
        assertEquals(worker03.getNodeName(), buildStage.getTag(CITags.NODE_NAME));
        assertTrue(((String) buildStage.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final DDSpan buildStep = pipelineTrace04.get(1);
        assertEquals(worker03.getNodeName(), buildStep.getTag(CITags.NODE_NAME));
        assertTrue(((String) buildStep.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final List<DDSpan> pipelineTrace05 = tracerWriter.get(5);
        assertEquals(5, pipelineTrace05.size());

        final DDSpan validateBlock = pipelineTrace05.get(0);
        assertEquals("Validate", validateBlock.getResourceName());
        assertEquals(worker03.getNodeName(), validateBlock.getTag(CITags.NODE_NAME));
        assertTrue(((String) validateBlock.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final DDSpan validateStage01 = pipelineTrace05.get(1);
        assertNodeNameParallelBlock(validateStage01, worker01, worker02);

        final DDSpan validateStep01 = pipelineTrace05.get(2);
        assertEquals(validateStage01.getTag(CITags.NODE_NAME), validateStep01.getTag(CITags.NODE_NAME));
        assertEquals(validateStage01.getTag(CITags.NODE_LABELS), validateStep01.getTag(CITags.NODE_LABELS));

        final DDSpan validateStage02 = pipelineTrace05.get(3);
        assertNodeNameParallelBlock(validateStage02, worker01, worker02);

        final DDSpan validateStep02 = pipelineTrace05.get(4);
        assertEquals(validateStage02.getTag(CITags.NODE_NAME), validateStep02.getTag(CITags.NODE_NAME));
        assertEquals(validateStage02.getTag(CITags.NODE_LABELS), validateStep02.getTag(CITags.NODE_LABELS));


        final List<DDSpan> pipelineTrace06 = tracerWriter.get(6);
        assertEquals(2, pipelineTrace06.size());

        final DDSpan ciStage = pipelineTrace06.get(0);
        assertEquals("CI", ciStage.getResourceName());
        assertEquals(worker03.getNodeName(), ciStage.getTag(CITags.NODE_NAME));
        assertTrue(((String) ciStage.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));

        final DDSpan ciStep = pipelineTrace06.get(1);
        assertEquals(worker03.getNodeName(), ciStep.getTag(CITags.NODE_NAME));
        assertTrue(((String) ciStep.getTag(CITags.NODE_LABELS)).contains(worker03.getNodeName()));
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

        //Traces
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("value", buildSpan.getTag("global_tag"));
        assertEquals("value", buildSpan.getTag("global_job_tag"));
        assertEquals("pipeline_tag_v2", buildSpan.getTag("pipeline_tag"));
        assertEquals("pipeline_tag", buildSpan.getTag("pipeline_tag_v2"));

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(2, pipelineTrace.size());
        final DDSpan stageSpan = pipelineTrace.get(0);
        assertEquals("value", stageSpan.getTag("global_tag"));
        assertEquals("value", stageSpan.getTag("global_job_tag"));
        assertEquals("pipeline_tag_v2", stageSpan.getTag("pipeline_tag"));
        assertEquals("pipeline_tag", stageSpan.getTag("pipeline_tag_v2"));

        final DDSpan stepSpan = pipelineTrace.get(1);
        assertEquals("value", stepSpan.getTag("global_tag"));
        assertEquals("value", stepSpan.getTag("global_job_tag"));
        assertEquals("pipeline_tag_v2", stepSpan.getTag("pipeline_tag"));
        assertEquals("pipeline_tag", stepSpan.getTag("pipeline_tag_v2"));
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

        //Traces
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("error", buildSpan.getTag(CITags.STATUS));

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(3, pipelineTrace.size());

        final DDSpan stageSpan = pipelineTrace.get(0);
        assertEquals("error", stageSpan.getTag(CITags.STATUS));

        final DDSpan stepSpan = pipelineTrace.get(1);
        assertEquals("error", stepSpan.getTag(CITags.STATUS));

        final DDSpan step2Span = pipelineTrace.get(2);
        assertEquals("error", step2Span.getTag(CITags.STATUS));
    }

    private void assertNodeNameParallelBlock(DDSpan stageSpan, DumbSlave worker01, DumbSlave worker02) {
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
