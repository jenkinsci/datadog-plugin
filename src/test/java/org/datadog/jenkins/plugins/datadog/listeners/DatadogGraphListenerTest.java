package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import datadog.trace.common.writer.ListWriter;
import datadog.trace.core.DDSpan;
import hudson.model.labels.LabelAtom;
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
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DatadogGraphListenerTest {

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private DatadogGraphListener listener;
    private DatadogClientStub clientStub;

    @BeforeClass
    public static void setup() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setCollectBuildTraces(true);
    }

    @Before
    public void beforeEach() {
        listener = new DatadogGraphListener();
        clientStub = new DatadogClientStub();
        ClientFactory.setTestClient(clientStub);
        clientStub.tracerWriter.start();
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

        //Traces
        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(2);
        assertEquals(2, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());
        final String buildPrefix = BuildPipelineNode.NodeType.PIPELINE.getNormalizedName();
        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("jenkins.build", buildSpan.getOperationName());
        assertEquals("jenkins", buildSpan.getServiceName());
        assertEquals("pipelineIntegrationSuccess", buildSpan.getResourceName());
        assertEquals("ci", buildSpan.getType());
        assertEquals("pipelineIntegrationSuccess", buildSpan.getTag(buildPrefix + CITags._NAME));
        assertEquals("SUCCESS", buildSpan.getTag(buildPrefix + CITags._RESULT));
        assertEquals("1", buildSpan.getTag(buildPrefix + CITags._ID));
        assertEquals("SUCCESS", buildSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", buildSpan.getTag(CITags.CI_PROVIDER));
        assertEquals("1", buildSpan.getTag(buildPrefix + CITags._NUMBER));
        assertEquals("anonymous", buildSpan.getTag(CITags.USER_NAME));
        assertEquals("jenkins-pipelineIntegrationSuccess-1", buildSpan.getTag(CITags.JENKINS_TAG));

        final List<DDSpan> pipelineTrace = tracerWriter.get(1);
        assertEquals(4, pipelineTrace.size());

        final String pipelinePrefix = BuildPipelineNode.NodeType.PIPELINE.getNormalizedName();
        final DDSpan pipelineSpan = pipelineTrace.get(0);
        assertEquals("jenkins.pipeline.internal", pipelineSpan.getOperationName());
        assertEquals("jenkins", pipelineSpan.getServiceName());
        assertEquals("Start of Pipeline", pipelineSpan.getResourceName());
        assertEquals("ci", pipelineSpan.getType());
        assertEquals("Start of Pipeline", pipelineSpan.getTag(pipelinePrefix + CITags._NAME));
        assertEquals("2", pipelineSpan.getTag(pipelinePrefix + CITags._ID));
        assertEquals("SUCCESS", pipelineSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", pipelineSpan.getTag(CITags.CI_PROVIDER));

        final String stepPrefix = BuildPipelineNode.NodeType.STEP.getNormalizedName();
        final DDSpan stepInternalSpan = pipelineTrace.get(1);
        assertEquals("jenkins.step.internal", stepInternalSpan.getOperationName());
        assertEquals("jenkins", stepInternalSpan.getServiceName());
        assertEquals("Stage : Start", stepInternalSpan.getResourceName());
        assertEquals("ci", stepInternalSpan.getType());
        assertEquals("Stage : Start", stepInternalSpan.getTag(stepPrefix + CITags._NAME));
        assertEquals("3", stepInternalSpan.getTag(stepPrefix + CITags._ID));
        assertEquals("SUCCESS", stepInternalSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stepInternalSpan.getTag(CITags.CI_PROVIDER));
        assertEquals("test", stepInternalSpan.getTag("jenkins.step.args.name"));
        assertNotNull(stepInternalSpan.getTag(stepPrefix + CITags._URL));

        final String stagePrefix = BuildPipelineNode.NodeType.STAGE.getNormalizedName();
        final DDSpan stageSpan = pipelineTrace.get(2);
        assertEquals("jenkins.stage", stageSpan.getOperationName());
        assertEquals("jenkins", stageSpan.getServiceName());
        assertEquals("test", stageSpan.getResourceName());
        assertEquals("ci", stageSpan.getType());
        assertEquals("test", stageSpan.getTag(stagePrefix + CITags._NAME));
        assertEquals("4", stageSpan.getTag(stagePrefix + CITags._ID));
        assertEquals("SUCCESS", stageSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stageSpan.getTag(CITags.CI_PROVIDER));

        final DDSpan stepAtomSpan = pipelineTrace.get(3);
        assertEquals("jenkins.step", stepAtomSpan.getOperationName());
        assertEquals("jenkins", stepAtomSpan.getServiceName());
        assertEquals("Print Message", stepAtomSpan.getResourceName());
        assertEquals("ci", stepAtomSpan.getType());
        assertEquals("Print Message", stepAtomSpan.getTag(stepPrefix + CITags._NAME));
        assertEquals("5", stepAtomSpan.getTag(stepPrefix + CITags._ID));
        assertEquals("SUCCESS", stepAtomSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", stepAtomSpan.getTag(CITags.CI_PROVIDER));
        assertEquals("hello", stepAtomSpan.getTag("jenkins.step.args.message"));
        assertNotNull(stepAtomSpan.getTag(stepPrefix + CITags._URL));
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

}
