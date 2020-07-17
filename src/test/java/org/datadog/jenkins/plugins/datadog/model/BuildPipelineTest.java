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
                BuildStage.buildStageKey("13", "Build Stage"),
                BuildStage.buildStageKey("15", "Inner Build Stage"),
                BuildStage.buildStageKey("17", "Inner Inner Build Stage")
        ), BuildStage.buildStage("17", "Inner Inner Build Stage").build());

        pipeline.addStage(Arrays.asList(
                BuildStage.buildStageKey("13", "Build Stage"),
                BuildStage.buildStageKey("15", "Inner Build Stage")
        ), BuildStage.buildStage("15", "Inner Build Stage").build());

        pipeline.addStage(Collections.singletonList(BuildStage.buildStageKey("13", "Build Stage")
        ), BuildStage.buildStage("13", "Build Stage").build());

        //When
        final BuildStage root = pipeline.buildTree();

        //Then
        Assert.assertEquals(1, root.getChildren().size());

        final List<BuildStage> rootChildren = root.getChildren();
        final BuildStage buildStage = rootChildren.get(0);
        Assert.assertEquals(BuildStage.buildStageKey("13", "Build Stage"), buildStage.getKey());
        Assert.assertEquals(1, buildStage.getChildren().size());

        final List<BuildStage> buildChildren = buildStage.getChildren();
        final BuildStage innerBuildStage = buildChildren.get(0);
        Assert.assertEquals(BuildStage.buildStageKey("15", "Inner Build Stage"), innerBuildStage.getKey());
        Assert.assertEquals(1, innerBuildStage.getChildren().size());

        final List<BuildStage> innerBuildChildren = innerBuildStage.getChildren();
        final BuildStage innerInnerBuildChildren = innerBuildChildren.get(0);
        Assert.assertEquals(BuildStage.buildStageKey("17", "Inner Inner Build Stage"), innerInnerBuildChildren.getKey());
        Assert.assertEquals(0, innerInnerBuildChildren.getChildren().size());
    }

    @Test
    public void should_build_pipeline_with_siblings_stages() {
        //Given
        final BuildPipeline pipeline = BuildPipeline.newPipeline();
        pipeline.addStage(Collections.singletonList(BuildStage.buildStageKey("6", "Declarative: Checkout SCM"))
                , BuildStage.buildStage("6", "Declarative: Checkout SCM").build());

        pipeline.addStage(Collections.singletonList(BuildStage.buildStageKey("13", "Build Stage")
        ), BuildStage.buildStage("13", "Build Stage").build());


        pipeline.addStage(Collections.singletonList(BuildStage.buildStageKey("26", "Test Stage"))
                , BuildStage.buildStage("26", "Test Stage").build());

        pipeline.addStage(Collections.singletonList(BuildStage.buildStageKey("35", "Deploy Stage"))
                , BuildStage.buildStage("35", "Deploy Stage").build());

        pipeline.addStage(Collections.singletonList(BuildStage.buildStageKey("14", "Declarative: Post Actions"))
                , BuildStage.buildStage("14", "Declarative: Post Actions").build());

        //When
        final BuildStage root = pipeline.buildTree();

        //Then
        Assert.assertEquals(5, root.getChildren().size());
    }

}