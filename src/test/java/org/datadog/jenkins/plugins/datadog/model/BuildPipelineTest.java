package org.datadog.jenkins.plugins.datadog.model;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.junit.Test;

import java.util.Arrays;

public class BuildPipelineTest {

    private static final String SAMPLE_FLOW_START_NODE_ID = "flowStartNodeId";
    private static final String SAMPLE_FLOW_START_NODE_NAME = "flowStartNodeName";
    private static final String SAMPLE_FLOW_END_NODE_NAME = "flowEndNodeName";
    private static final String SAMPLE_FLOW_END_NODE_ID = "flowEndNodeId";
    private static final String SAMPLE_BLOCK_START_NODE_ID = "blockStartNodeId";
    private static final String SAMPLE_BLOCK_START_NODE_NAME = "blockStartNodeName";

    private static final String SAMPLE_BLOCK_END_NODE_ID = "blockEndNodeId";
    private static final String SAMPLE_BLOCK_END_NODE_NAME = "blockEndNodeName";

    private static final String SAMPLE_STEP_START_NODE_ID = "stepStartNodeId";
    private static final String SAMPLE_STEP_START_NODE_NAME = "stepStartNodeName";
    private static final String SAMPLE_STEP_END_NODE_ID = "stepEndNodeId";
    private static final String SAMPLE_STEP_END_NODE_NAME = "stepEndNodeName";

    private static final String SAMPLE_STEP_ATOM_NODE_ONE_ID = "stepAtomNodeOneId";
    private static final String SAMPLE_STEP_ATOM_NODE_ONE_NAME = "stepAtomNodeOneName";
    private static final String SAMPLE_STEP_ATOM_NODE_TWO_ID = "stepAtomNodeTwoId";
    private static final String SAMPLE_STEP_ATOM_NODE_TWO_NAME = "stepAtomNodeTwoName";

    private static final Long SAMPLE_TIME = 999L;

    @Test
    public void should_build_pipeline_with_nested_stages() {
        //Given
        final BuildPipeline pipeline = new BuildPipeline();

        final TimingAction mockTimingAction = mock(TimingAction.class);
        when(mockTimingAction.getStartTime()).thenReturn(SAMPLE_TIME);

        final FlowStartNode flowStartNode = mock(FlowStartNode.class);
        when(flowStartNode.getId()).thenReturn(SAMPLE_FLOW_START_NODE_ID);
        when(flowStartNode.getDisplayName()).thenReturn(SAMPLE_FLOW_START_NODE_NAME);
        when(flowStartNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);


        final StepStartNode blockStartNode = mock(StepStartNode.class);
        when(blockStartNode.getId()).thenReturn(SAMPLE_BLOCK_START_NODE_ID);
        when(blockStartNode.getDisplayName()).thenReturn(SAMPLE_BLOCK_START_NODE_NAME);
        when(blockStartNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);


        final StepStartNode stepStartNode = mock(StepStartNode.class);
        when(stepStartNode.getId()).thenReturn(SAMPLE_STEP_START_NODE_ID);
        when(stepStartNode.getDisplayName()).thenReturn(SAMPLE_STEP_START_NODE_NAME);
        when(stepStartNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);

        final TimingAction mockTimingActionAtomOne = mock(TimingAction.class);
        when(mockTimingActionAtomOne.getStartTime()).thenReturn(SAMPLE_TIME + 100);

        final StepAtomNode stepAtomNodeOne = mock(StepAtomNode.class);
        when(stepAtomNodeOne.getId()).thenReturn(SAMPLE_STEP_ATOM_NODE_ONE_ID);
        when(stepAtomNodeOne.getDisplayName()).thenReturn(SAMPLE_STEP_ATOM_NODE_ONE_NAME);
        when(stepAtomNodeOne.iterateEnclosingBlocks()).thenReturn(Arrays.asList(stepStartNode, blockStartNode, flowStartNode));
        when(stepAtomNodeOne.getAction(TimingAction.class)).thenReturn(mockTimingActionAtomOne);

        final TimingAction mockTimingActionAtomTwo = mock(TimingAction.class);
        when(mockTimingActionAtomTwo.getStartTime()).thenReturn(SAMPLE_TIME + 200);

        final StepAtomNode stepAtomNodeTwo = mock(StepAtomNode.class);
        when(stepAtomNodeTwo.getId()).thenReturn(SAMPLE_STEP_ATOM_NODE_TWO_ID);
        when(stepAtomNodeTwo.getDisplayName()).thenReturn(SAMPLE_STEP_ATOM_NODE_TWO_NAME);
        when(stepAtomNodeTwo.iterateEnclosingBlocks()).thenReturn(Arrays.asList(stepStartNode, blockStartNode, flowStartNode));
        when(stepAtomNodeTwo.getAction(TimingAction.class)).thenReturn(mockTimingActionAtomTwo);


        final StepEndNode stepEndNode = mock(StepEndNode.class);
        when(stepEndNode.getId()).thenReturn(SAMPLE_STEP_END_NODE_ID);
        when(stepEndNode.getDisplayName()).thenReturn(SAMPLE_STEP_END_NODE_NAME);
        when(stepEndNode.getStartNode()).thenReturn(stepStartNode);
        when(stepEndNode.iterateEnclosingBlocks()).thenReturn(Arrays.asList(blockStartNode, flowStartNode));
        when(stepEndNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);


        final StepEndNode blockEndNode = mock(StepEndNode.class);
        when(blockEndNode.getId()).thenReturn(SAMPLE_BLOCK_END_NODE_ID);
        when(blockEndNode.getDisplayName()).thenReturn(SAMPLE_BLOCK_END_NODE_NAME);
        when(blockEndNode.getStartNode()).thenReturn(blockStartNode);
        when(blockEndNode.iterateEnclosingBlocks()).thenReturn(Arrays.asList(flowStartNode));
        when(blockEndNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);


        final FlowEndNode flowEndNode = mock(FlowEndNode.class);
        when(flowEndNode.getId()).thenReturn(SAMPLE_FLOW_END_NODE_ID);
        when(flowEndNode.getDisplayName()).thenReturn(SAMPLE_FLOW_END_NODE_NAME);
        when(flowEndNode.getStartNode()).thenReturn(flowStartNode);
        when(flowEndNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);
        when(flowEndNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);

        pipeline.add(flowEndNode);
        pipeline.add(blockEndNode);
        pipeline.add(stepEndNode);
        pipeline.add(stepAtomNodeTwo);
        pipeline.add(stepAtomNodeOne);
        pipeline.add(stepStartNode);
        pipeline.add(blockStartNode);
        pipeline.add(flowStartNode);


        //When
        final BuildPipelineNode pipelineRoot = pipeline.buildTree();

        //Then
        assertNode(pipelineRoot, SAMPLE_FLOW_START_NODE_ID, SAMPLE_FLOW_START_NODE_NAME, SAMPLE_TIME * 1000, SAMPLE_TIME * 1000, 1);

        final BuildPipelineNode rootChild = pipelineRoot.getChildren().get(0);
        assertNode(rootChild, SAMPLE_BLOCK_START_NODE_ID, SAMPLE_BLOCK_START_NODE_NAME, SAMPLE_TIME * 1000, SAMPLE_TIME * 1000, 1);

        final BuildPipelineNode rootChildChild = rootChild.getChildren().get(0);
        assertNode(rootChildChild, SAMPLE_STEP_START_NODE_ID, SAMPLE_STEP_START_NODE_NAME, SAMPLE_TIME * 1000, SAMPLE_TIME * 1000, 2);

        final BuildPipelineNode atomOne = rootChildChild.getChildren().get(0);
        assertNode(atomOne, SAMPLE_STEP_ATOM_NODE_ONE_ID, SAMPLE_STEP_ATOM_NODE_ONE_NAME, (SAMPLE_TIME + 100) * 1000, (SAMPLE_TIME + 200) * 1000, 0);

        final BuildPipelineNode atomTwo = rootChildChild.getChildren().get(1);
        assertNode(atomTwo, SAMPLE_STEP_ATOM_NODE_TWO_ID, SAMPLE_STEP_ATOM_NODE_TWO_NAME, (SAMPLE_TIME + 200) * 1000, SAMPLE_TIME * 1000, 0);
    }

    private void assertNode(BuildPipelineNode node, String expectedId, String expectedName, long expectedStartTimeMicros, long expectedEndTimeMicros, int expectedChildrenSize) {
        assertEquals(expectedId, node.getId());
        assertEquals(expectedName, node.getName());
        assertEquals(expectedStartTimeMicros , node.getStartTimeMicros());
        assertEquals(expectedEndTimeMicros , node.getEndTimeMicros());
        assertEquals(expectedChildrenSize, node.getChildren().size());
    }
}