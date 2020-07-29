package org.datadog.jenkins.plugins.datadog.model;

import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_BLOCK_END_NODE_ID;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_BLOCK_START_NODE_ID;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_BLOCK_START_NODE_NAME;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_FLOW_END_NODE_ID;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_FLOW_START_NODE_ID;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_FLOW_START_NODE_NAME;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_STEP_ATOM_NODE_ONE_ID;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_STEP_ATOM_NODE_ONE_NAME;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_STEP_ATOM_NODE_TWO_ID;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_STEP_ATOM_NODE_TWO_NAME;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_STEP_END_NODE_ID;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_STEP_START_NODE_ID;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_STEP_START_NODE_NAME;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.SAMPLE_TIME;
import static org.datadog.jenkins.plugins.datadog.traces.DatadogTracesUtilities.getDummyPipeline;
import static org.junit.Assert.assertEquals;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.junit.Test;

import java.util.Map;

public class BuildPipelineTest {

    @Test
    public void should_build_pipeline_with_nested_stages() {
        //Given
        final Map<String, FlowNode> flowNodeById = getDummyPipeline();
        final BuildPipeline pipeline = new BuildPipeline();
        pipeline.add(flowNodeById.get(SAMPLE_FLOW_END_NODE_ID));
        pipeline.add(flowNodeById.get(SAMPLE_BLOCK_END_NODE_ID));
        pipeline.add(flowNodeById.get(SAMPLE_STEP_END_NODE_ID));
        pipeline.add(flowNodeById.get(SAMPLE_STEP_ATOM_NODE_TWO_ID));
        pipeline.add(flowNodeById.get(SAMPLE_STEP_ATOM_NODE_ONE_ID));
        pipeline.add(flowNodeById.get(SAMPLE_STEP_START_NODE_ID));
        pipeline.add(flowNodeById.get(SAMPLE_BLOCK_START_NODE_ID));
        pipeline.add(flowNodeById.get(SAMPLE_FLOW_START_NODE_ID));

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