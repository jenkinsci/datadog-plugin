package org.datadog.jenkins.plugins.datadog.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import hudson.model.labels.LabelAtom;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DatadogGraphListenerTest {

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private DatadogGraphListener listener;
    private DatadogClientStub clientStub;

    @Before
    public void beforeEach() {
        listener = new DatadogGraphListener();
        clientStub = new DatadogClientStub();
        ClientFactory.setTestClient(clientStub);
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
        String[] expectedTags = new String[]{
                "jenkins_url:" + DatadogUtilities.getJenkinsUrl(),
                "user_id:anonymous",
                "stage_name:low",
                "job:pipeline",
                "parent_stage_name:medium",
                "stage_depth:2",
                "result:Unknown"
        };
        clientStub.assertMetric("jenkins.job.stage_duration", endTime - startTime, hostname, expectedTags);
    }
    
    @Test
    public void testIntegration() throws Exception {
        listener = spy(DatadogGraphListener.class);
        // CI and local tests return different results
        doReturn("Unknown").when(listener).getResultTag(any());
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
                "result:Unknown"
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

    }
    
    @Test
    public void testIntegrationErrorTag() throws Exception {
        jenkinsRule.createOnlineSlave(new LabelAtom("windows"));
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineIntegrationFailure");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("testPipelineFailure.txt"),
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
        String[] tags = new String[]{
                "jenkins_url:" + DatadogUtilities.getJenkinsUrl(),
                "user_id:anonymous",
                "job:pipelineIntegrationFailure",
                "result:Error",
                "stage_depth:0",
                "stage_name:Fail",
                "parent_stage_name:root"               
        };
        clientStub.assertMetric("jenkins.job.stage_duration", hostname, tags);
    }

    @Test
    public void isStageNodeTest() {
        Assert.assertFalse(listener.isStageNode(null));
        BlockStartNode node = mock(BlockStartNode.class);
        Assert.assertFalse(listener.isStageNode(node));

        when(node.getAction(LabelAction.class)).thenReturn(mock(LabelAction.class));
        Assert.assertTrue(listener.isStageNode(node));

        when(node.getAction(ThreadNameAction.class)).thenReturn(mock(ThreadNameAction.class));
        Assert.assertFalse(listener.isStageNode(node));
    }

    @Test
    public void getStageNameTest() {
        String stageName = "Hello world";
        StepStartNode node = mock(StepStartNode.class);
        when(node.getDisplayName()).thenReturn(stageName);

        // Regular stage with no thread
        when(node.getAction(ThreadNameAction.class)).thenReturn(null);
        Assert.assertEquals(listener.getStageName(node), stageName);

        // Parallel stage
        String stageNameThread = "Hello thread";
        ThreadNameAction threadNameAction = mock(ThreadNameAction.class);
        when(threadNameAction.getThreadName()).thenReturn(stageNameThread);
        when(node.getAction(ThreadNameAction.class)).thenReturn(threadNameAction);
        Assert.assertEquals(listener.getStageName(node), stageNameThread);
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

        Assert.assertEquals(endTime - startTime, listener.getTime(startNode, endNode));
    }

}
