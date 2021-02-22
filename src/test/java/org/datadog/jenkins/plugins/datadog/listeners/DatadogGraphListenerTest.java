package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import datadog.trace.api.IdGenerationStrategy;
import datadog.trace.common.writer.ListWriter;
import datadog.trace.core.DDSpan;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
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
        cfg.setTraceIdsGenerator(IdGenerationStrategy.RANDOM);

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
        }

        //Traces
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(29, pipelineTrace.size());
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
        assertEquals(16, pipelineTrace.size());

        final DDSpan stage2 = pipelineTrace.get(6);
        final String stage2Name = (String) stage2.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage2Name != null && !stage2Name.isEmpty());

        final DDSpan allocateNodeStart2 = pipelineTrace.get(7);
        assertEquals(stage2Name, allocateNodeStart2.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));

        final DDSpan allocateNodeBodyStart2 = pipelineTrace.get(8);
        assertEquals(stage2Name, allocateNodeBodyStart2.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));

        final DDSpan stepStage2 = pipelineTrace.get(9);
        assertEquals(stage2Name, stepStage2.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));

        final DDSpan stage1 = pipelineTrace.get(12);
        final String stage1Name = (String) stage1.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME);
        assertTrue(stage1Name != null && !stage1Name.isEmpty());

        final DDSpan allocateNodeStart1 = pipelineTrace.get(13);
        assertEquals(stage1Name, allocateNodeStart1.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));

        final DDSpan allocateNodeBodyStart1 = pipelineTrace.get(14);
        assertEquals(stage1Name, allocateNodeBodyStart1.getTag(BuildPipelineNode.NodeType.STAGE.getTagName() + CITags._NAME));

        final DDSpan stepStage1 = pipelineTrace.get(15);
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
        Thread.sleep(5000);
        jenkinsRule.createOnlineSlave(Label.get("testStage"));


        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals(0L, buildSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(16, pipelineTrace.size());

        final DDSpan startPipeline = pipelineTrace.get(0);
        assertEquals(0L, startPipeline.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan stageStart = pipelineTrace.get(1);
        assertEquals(0L, stageStart.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan runStages = pipelineTrace.get(2);
        assertEquals(0L, runStages.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan executeInParallelStart = pipelineTrace.get(3);
        assertEquals(0L, executeInParallelStart.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan branchStage2 = pipelineTrace.get(4);
        assertEquals(0L, branchStage2.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan stage2Start = pipelineTrace.get(5);
        assertEquals(0L, stage2Start.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan stage2 = pipelineTrace.get(6);
        long stage2QueueTime = (long) stage2.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage2QueueTime > 0L);

        final DDSpan allocateNodeStart2 = pipelineTrace.get(7);
        assertEquals(stage2QueueTime, allocateNodeStart2.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan allocateNodeBodyStart2 = pipelineTrace.get(8);
        assertEquals(0L, allocateNodeBodyStart2.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan stepStage2 = pipelineTrace.get(9);
        assertEquals(0L, stepStage2.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan branchStage1 = pipelineTrace.get(10);
        assertEquals(0L, branchStage1.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan stage1Start = pipelineTrace.get(11);
        assertEquals(0L, stage1Start.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan stage1 = pipelineTrace.get(12);
        long stage1QueueTime = (long) stage1.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(stage1QueueTime > 0L);

        final DDSpan allocateNodeStart1 = pipelineTrace.get(13);
        assertEquals(stage1QueueTime, allocateNodeStart1.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan allocateNodeBodyStart1 = pipelineTrace.get(14);
        assertEquals(0L, allocateNodeBodyStart1.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan stepStage1 = pipelineTrace.get(15);
        assertEquals(0L, stepStage1.getUnsafeMetrics().get(CITags.QUEUE_TIME));
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
        Thread.sleep(5000);
        jenkinsRule.createOnlineSlave(Label.get("testPipeline"));

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final DDSpan buildSpan = buildTrace.get(0);
        long queueTime = (long) buildSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME);
        assertTrue(queueTime > 0L);
        assertEquals("testPipeline", buildSpan.getTag(CITags.NODE_NAME));
        assertEquals("none",buildSpan.getTag(CITags._DD_HOSTNAME));

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(6, pipelineTrace.size());
        assertEquals("testPipeline", buildSpan.getTag(CITags.NODE_NAME));
        assertEquals("none",buildSpan.getTag(CITags._DD_HOSTNAME));

        final DDSpan startPipeline = pipelineTrace.get(0);
        assertEquals(queueTime, startPipeline.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("testPipeline", buildSpan.getTag(CITags.NODE_NAME));
        assertEquals("none",buildSpan.getTag(CITags._DD_HOSTNAME));

        final DDSpan allocateNode = pipelineTrace.get(1);
        assertEquals(queueTime, allocateNode.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("testPipeline", buildSpan.getTag(CITags.NODE_NAME));
        assertEquals("none",buildSpan.getTag(CITags._DD_HOSTNAME));

        final DDSpan allocateNodeStart = pipelineTrace.get(2);
        assertEquals(0L, allocateNodeStart.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("testPipeline", buildSpan.getTag(CITags.NODE_NAME));
        assertEquals("none",buildSpan.getTag(CITags._DD_HOSTNAME));

        final DDSpan stageStart = pipelineTrace.get(3);
        assertEquals(0L, stageStart.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("testPipeline", buildSpan.getTag(CITags.NODE_NAME));
        assertEquals("none",buildSpan.getTag(CITags._DD_HOSTNAME));

        final DDSpan stage = pipelineTrace.get(4);
        assertEquals(0L, stage.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("testPipeline", buildSpan.getTag(CITags.NODE_NAME));
        assertEquals("none",buildSpan.getTag(CITags._DD_HOSTNAME));

        final DDSpan step = pipelineTrace.get(5);
        assertEquals(0L, step.getUnsafeMetrics().get(CITags.QUEUE_TIME));
        assertEquals("testPipeline", buildSpan.getTag(CITags.NODE_NAME));
        assertEquals("none",buildSpan.getTag(CITags._DD_HOSTNAME));
    }

    @Test
    public void testIntegrationNoFailureTag() throws Exception {
        final DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setTraceIdsGenerator(IdGenerationStrategy.SEQUENTIAL);

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
        assertNull(buildSpan.getTag(CITags._DD_HOSTNAME));
        assertEquals("success", buildSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", buildSpan.getTag(CITags.JENKINS_TAG));
        assertEquals(false, buildSpan.getTag(CITags._DD_CI_INTERNAL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpan.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpan.getTag(CITags._DD_CI_LEVEL));
        assertNotNull(buildSpan.getTag(CITags._DD_CI_STAGES));
        assertTrue(((String) buildSpan.getTag(CITags._DD_CI_STAGES)).contains("{\"name\":\"test\",\"duration\""));

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(4, pipelineTrace.size());

        final String pipelinePrefix = BuildPipelineNode.NodeType.STEP.getTagName();
        final DDSpan pipelineSpan = pipelineTrace.get(0);
        assertEquals("jenkins.step.internal", pipelineSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, pipelineSpan.getServiceName());
        assertEquals("Start of Pipeline", pipelineSpan.getResourceName());
        assertEquals("ci", pipelineSpan.getType());
        assertEquals("Start of Pipeline", pipelineSpan.getTag(pipelinePrefix + CITags._NAME));
        assertEquals("2", pipelineSpan.getTag(pipelinePrefix + CITags._NUMBER));
        assertEquals("success", pipelineSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", pipelineSpan.getTag(CITags.CI_PROVIDER_NAME));
        assertNotNull(pipelineSpan.getTag(pipelinePrefix + CITags._URL));
        assertNotNull(pipelineSpan.getTag(CITags.NODE_NAME));
        assertNull(pipelineSpan.getTag(CITags._DD_HOSTNAME));
        assertEquals(true, pipelineSpan.getTag(CITags._DD_CI_INTERNAL));
        assertNull(pipelineSpan.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertNull(pipelineSpan.getTag(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", pipelineSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", pipelineSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME));
        assertNotNull(pipelineSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final String stepPrefix = BuildPipelineNode.NodeType.STEP.getTagName();
        final DDSpan stepInternalSpan = pipelineTrace.get(1);
        assertEquals("1", stepInternalSpan.context().getSpanId().toString());
        assertEquals("jenkins.step.internal", stepInternalSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, stepInternalSpan.getServiceName());
        assertEquals("Stage : Start", stepInternalSpan.getResourceName());
        assertEquals("ci", stepInternalSpan.getType());
        assertEquals("Stage : Start", stepInternalSpan.getTag(stepPrefix + CITags._NAME));
        assertEquals("success", stepInternalSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stepInternalSpan.getTag(CITags.CI_PROVIDER_NAME));
        assertEquals("test", stepInternalSpan.getTag("jenkins.step.args.name"));
        assertNotNull(stepInternalSpan.getTag(stepPrefix + CITags._URL));
        assertNotNull(stepInternalSpan.getTag(CITags.NODE_NAME));
        assertNull(stepInternalSpan.getTag(CITags._DD_HOSTNAME));
        assertEquals(true, stepInternalSpan.getTag(CITags._DD_CI_INTERNAL));
        assertEquals("3", stepInternalSpan.getTag(stepPrefix + CITags._NUMBER));
        assertNull(stepInternalSpan.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertNull(stepInternalSpan.getTag(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stepInternalSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stepInternalSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME));
        assertNotNull(stepInternalSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final String stagePrefix = BuildPipelineNode.NodeType.STAGE.getTagName();
        final DDSpan stageSpan = pipelineTrace.get(2);
        assertEquals("1", stageSpan.context().getParentId().toString());
        assertEquals("jenkins.stage", stageSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, stageSpan.getServiceName());
        assertEquals("test", stageSpan.getResourceName());
        assertEquals("ci", stageSpan.getType());
        assertEquals("test", stageSpan.getTag(stagePrefix + CITags._NAME));
        assertEquals("success", stageSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stageSpan.getTag(CITags.CI_PROVIDER_NAME));
        assertNotNull(stageSpan.getTag(stagePrefix + CITags._URL));
        assertNotNull(stageSpan.getTag(CITags.NODE_NAME));
        assertNull(stageSpan.getTag(CITags._DD_HOSTNAME));
        assertEquals(false, stageSpan.getTag(CITags._DD_CI_INTERNAL));
        assertEquals("4", stageSpan.getTag(stagePrefix + CITags._NUMBER));
        assertEquals(BuildPipelineNode.NodeType.STAGE.getBuildLevel(), stageSpan.getTag(CITags._DD_CI_BUILD_LEVEL));
        assertEquals(BuildPipelineNode.NodeType.STAGE.getBuildLevel(), stageSpan.getTag(CITags._DD_CI_LEVEL));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", stageSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._ID));
        assertEquals("pipelineIntegrationSuccess", stageSpan.getTag(BuildPipelineNode.NodeType.PIPELINE.getTagName() + CITags._NAME));
        assertNotNull(stageSpan.getUnsafeMetrics().get(CITags.QUEUE_TIME));

        final DDSpan stepAtomSpan = pipelineTrace.get(3);
        assertEquals("2", stepAtomSpan.context().getSpanId().toString());
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
