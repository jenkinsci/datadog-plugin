package org.datadog.jenkins.plugins.datadog.traces;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatadogTracesUtilities {

    public static final Long SAMPLE_TIME = 999L;

    public static final String SAMPLE_FLOW_START_NODE_ID = "flowStartNodeId";
    public static final String SAMPLE_FLOW_START_NODE_NAME = "flowStartNodeName";
    public static final String SAMPLE_FLOW_END_NODE_NAME = "flowEndNodeName";
    public static final String SAMPLE_FLOW_END_NODE_ID = "flowEndNodeId";
    public static final String SAMPLE_BLOCK_START_NODE_ID = "blockStartNodeId";
    public static final String SAMPLE_BLOCK_START_NODE_NAME = "blockStartNodeName";

    public static final String SAMPLE_BLOCK_END_NODE_ID = "blockEndNodeId";
    public static final String SAMPLE_BLOCK_END_NODE_NAME = "blockEndNodeName";

    public static final String SAMPLE_STEP_START_NODE_ID = "stepStartNodeId";
    public static final String SAMPLE_STEP_START_NODE_NAME = "stepStartNodeName";
    public static final String SAMPLE_STEP_END_NODE_ID = "stepEndNodeId";
    public static final String SAMPLE_STEP_END_NODE_NAME = "stepEndNodeName";

    public static final String SAMPLE_STEP_ATOM_NODE_ONE_ID = "stepAtomNodeOneId";
    public static final String SAMPLE_STEP_ATOM_NODE_ONE_NAME = "stepAtomNodeOneName";
    public static final String SAMPLE_STEP_ATOM_NODE_TWO_ID = "stepAtomNodeTwoId";
    public static final String SAMPLE_STEP_ATOM_NODE_TWO_NAME = "stepAtomNodeTwoName";

    private static Map<String, FlowNode> flowNodeById;

    public static Map<String, FlowNode> getDummyPipeline() {
        if(flowNodeById == null) {
            flowNodeById = new HashMap<>();

            final TimingAction mockTimingAction = mock(TimingAction.class);
            when(mockTimingAction.getStartTime()).thenReturn(SAMPLE_TIME);

            final FlowStartNode flowStartNode = mock(FlowStartNode.class);
            when(flowStartNode.getId()).thenReturn(SAMPLE_FLOW_START_NODE_ID);
            when(flowStartNode.getDisplayName()).thenReturn(SAMPLE_FLOW_START_NODE_NAME);
            when(flowStartNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);
            flowNodeById.put(SAMPLE_FLOW_START_NODE_ID, flowStartNode);

            final StepStartNode blockStartNode = mock(StepStartNode.class);
            when(blockStartNode.getId()).thenReturn(SAMPLE_BLOCK_START_NODE_ID);
            when(blockStartNode.getDisplayName()).thenReturn(SAMPLE_BLOCK_START_NODE_NAME);
            when(blockStartNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);
            flowNodeById.put(SAMPLE_BLOCK_START_NODE_ID, blockStartNode);

            final StepStartNode stepStartNode = mock(StepStartNode.class);
            when(stepStartNode.getId()).thenReturn(SAMPLE_STEP_START_NODE_ID);
            when(stepStartNode.getDisplayName()).thenReturn(SAMPLE_STEP_START_NODE_NAME);
            when(stepStartNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);
            flowNodeById.put(SAMPLE_STEP_START_NODE_ID, stepStartNode);

            final TimingAction mockTimingActionAtomOne = mock(TimingAction.class);
            when(mockTimingActionAtomOne.getStartTime()).thenReturn(SAMPLE_TIME + 100);

            final StepAtomNode stepAtomNodeOne = mock(StepAtomNode.class);
            when(stepAtomNodeOne.getId()).thenReturn(SAMPLE_STEP_ATOM_NODE_ONE_ID);
            when(stepAtomNodeOne.getDisplayName()).thenReturn(SAMPLE_STEP_ATOM_NODE_ONE_NAME);
            when(stepAtomNodeOne.iterateEnclosingBlocks()).thenReturn(Arrays.asList(stepStartNode, blockStartNode, flowStartNode));
            when(stepAtomNodeOne.getAction(TimingAction.class)).thenReturn(mockTimingActionAtomOne);
            flowNodeById.put(SAMPLE_STEP_ATOM_NODE_ONE_ID, stepAtomNodeOne);

            final TimingAction mockTimingActionAtomTwo = mock(TimingAction.class);
            when(mockTimingActionAtomTwo.getStartTime()).thenReturn(SAMPLE_TIME + 200);

            final StepAtomNode stepAtomNodeTwo = mock(StepAtomNode.class);
            when(stepAtomNodeTwo.getId()).thenReturn(SAMPLE_STEP_ATOM_NODE_TWO_ID);
            when(stepAtomNodeTwo.getDisplayName()).thenReturn(SAMPLE_STEP_ATOM_NODE_TWO_NAME);
            when(stepAtomNodeTwo.iterateEnclosingBlocks()).thenReturn(Arrays.asList(stepStartNode, blockStartNode, flowStartNode));
            when(stepAtomNodeTwo.getAction(TimingAction.class)).thenReturn(mockTimingActionAtomTwo);
            flowNodeById.put(SAMPLE_STEP_ATOM_NODE_TWO_ID, stepAtomNodeTwo);

            final StepEndNode stepEndNode = mock(StepEndNode.class);
            when(stepEndNode.getId()).thenReturn(SAMPLE_STEP_END_NODE_ID);
            when(stepEndNode.getDisplayName()).thenReturn(SAMPLE_STEP_END_NODE_NAME);
            when(stepEndNode.getStartNode()).thenReturn(stepStartNode);
            when(stepEndNode.iterateEnclosingBlocks()).thenReturn(Arrays.asList(blockStartNode, flowStartNode));
            when(stepEndNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);
            flowNodeById.put(SAMPLE_STEP_END_NODE_ID, stepEndNode);


            final StepEndNode blockEndNode = mock(StepEndNode.class);
            when(blockEndNode.getId()).thenReturn(SAMPLE_BLOCK_END_NODE_ID);
            when(blockEndNode.getDisplayName()).thenReturn(SAMPLE_BLOCK_END_NODE_NAME);
            when(blockEndNode.getStartNode()).thenReturn(blockStartNode);
            when(blockEndNode.iterateEnclosingBlocks()).thenReturn(List.of(flowStartNode));
            when(blockEndNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);
            flowNodeById.put(SAMPLE_BLOCK_END_NODE_ID, blockEndNode);

            final FlowEndNode flowEndNode = mock(FlowEndNode.class);
            when(flowEndNode.getId()).thenReturn(SAMPLE_FLOW_END_NODE_ID);
            when(flowEndNode.getDisplayName()).thenReturn(SAMPLE_FLOW_END_NODE_NAME);
            when(flowEndNode.getStartNode()).thenReturn(flowStartNode);
            when(flowEndNode.getAction(TimingAction.class)).thenReturn(mockTimingAction);
            final FlowExecution flowExecution = mock(FlowExecution.class);
            when(flowExecution.getCurrentHeads()).thenReturn(Collections.singletonList(flowEndNode));
            when(flowEndNode.getExecution()).thenReturn(flowExecution);
            flowNodeById.put(SAMPLE_FLOW_END_NODE_ID, flowEndNode);
        }

        return flowNodeById;
    }
}
