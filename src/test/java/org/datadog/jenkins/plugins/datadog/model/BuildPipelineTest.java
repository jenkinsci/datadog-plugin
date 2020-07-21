package org.datadog.jenkins.plugins.datadog.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BuildPipelineTest {

    @Test
    public void should_build_pipeline_with_nested_stages() {
        //Given
        final BuildPipeline pipeline = BuildPipeline.newPipeline();
        pipeline.addStage(Arrays.asList(
                BuildPipelineNode.buildStageKey("13", "Build Stage"),
                BuildPipelineNode.buildStageKey("15", "Inner Build Stage"),
                BuildPipelineNode.buildStageKey("17", "Inner Inner Build Stage")
        ), BuildPipelineNode.buildStage("17", "Inner Inner Build Stage").build());

        pipeline.addStage(Arrays.asList(
                BuildPipelineNode.buildStageKey("13", "Build Stage"),
                BuildPipelineNode.buildStageKey("15", "Inner Build Stage")
        ), BuildPipelineNode.buildStage("15", "Inner Build Stage").build());

        pipeline.addStage(Collections.singletonList(BuildPipelineNode.buildStageKey("13", "Build Stage")
        ), BuildPipelineNode.buildStage("13", "Build Stage").build());

        //When
        final BuildPipelineNode buildPipelineNode = pipeline.buildTree();

        //Then
        Assert.assertEquals(BuildPipelineNode.buildStageKey("13", "Build Stage"), buildPipelineNode.getKey());
        Assert.assertEquals(1, buildPipelineNode.getChildren().size());

        final List<BuildPipelineNode> buildChildren = buildPipelineNode.getChildren();
        final BuildPipelineNode innerBuildPipelineNode = buildChildren.get(0);
        Assert.assertEquals(BuildPipelineNode.buildStageKey("15", "Inner Build Stage"), innerBuildPipelineNode.getKey());
        Assert.assertEquals(1, innerBuildPipelineNode.getChildren().size());

        final List<BuildPipelineNode> innerBuildChildren = innerBuildPipelineNode.getChildren();
        final BuildPipelineNode innerInnerBuildChildren = innerBuildChildren.get(0);
        Assert.assertEquals(BuildPipelineNode.buildStageKey("17", "Inner Inner Build Stage"), innerInnerBuildChildren.getKey());
        Assert.assertEquals(0, innerInnerBuildChildren.getChildren().size());
    }

    @Test
    public void should_build_pipeline_with_siblings_stages() {
        //Given
        final BuildPipeline pipeline = BuildPipeline.newPipeline();
        pipeline.addStage(Collections.singletonList(BuildPipelineNode.buildStageKey("6", "Declarative: Checkout SCM"))
                , BuildPipelineNode.buildStage("6", "Declarative: Checkout SCM").build());

        pipeline.addStage(Collections.singletonList(BuildPipelineNode.buildStageKey("13", "Build Stage")
        ), BuildPipelineNode.buildStage("13", "Build Stage").build());


        pipeline.addStage(Collections.singletonList(BuildPipelineNode.buildStageKey("26", "Test Stage"))
                , BuildPipelineNode.buildStage("26", "Test Stage").build());

        pipeline.addStage(Collections.singletonList(BuildPipelineNode.buildStageKey("35", "Deploy Stage"))
                , BuildPipelineNode.buildStage("35", "Deploy Stage").build());

        pipeline.addStage(Collections.singletonList(BuildPipelineNode.buildStageKey("14", "Declarative: Post Actions"))
                , BuildPipelineNode.buildStage("14", "Declarative: Post Actions").build());

        //When
        final BuildPipelineNode root = pipeline.buildTree();

        //Then
        Assert.assertEquals(5, root.getChildren().size());
    }

}