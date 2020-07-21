package org.datadog.jenkins.plugins.datadog.model;

import static org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode.BuildStageKey;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Jenkins Pipeline.
 * To represent the stages is used an n-ary tree.
 */
public class BuildPipeline {

    private final Map<List<BuildStageKey>, BuildPipelineNode> stagesByPath;
    private BuildPipelineNode root;

    public static BuildPipeline newPipeline() {
        return new BuildPipeline();
    }

    public BuildPipeline() {
        this.stagesByPath = new HashMap<>();
        this.root = BuildPipelineNode.buildStage("initial", "initial").build();
    }

    public BuildPipelineNode addStage(final List<BuildStageKey> stageRelations, final BuildPipelineNode stage) {
        return stagesByPath.put(stageRelations, stage);
    }

    /**
     * Reconstruct a Jenkins pipeline tree from the info gathered in the {@code DatadogGraphListener}.
     * Example:
     * Starting from the stagesByPath:
     *   Key: (Stage1, Stage2) - Value: (Stage2)
     *   Key: (Stage1, Stage2, Stage3) - Value: (Stage3)
     *   Key: (Stage1) - Value: (Stage1)
     * it will be returned the following tree:
     *   root
     *     -- stage1
     *          -- stage2
     *               -- stage3
     **/
    public BuildPipelineNode buildTree() {
        for(Map.Entry<List<BuildStageKey>, BuildPipelineNode> entry : stagesByPath.entrySet()){
            final List<BuildStageKey> pathStages = entry.getKey();
            final BuildPipelineNode stage = entry.getValue();
            buildTree(pathStages, root, stage);
        }

        sortSiblingsByStartTime(root.getChildren());
        completeInformation(root.getChildren(), root);
        assignPipelineToRootNode(root);
        return root;
    }

    private void assignPipelineToRootNode(BuildPipelineNode root) {
        final List<BuildPipelineNode> children = root.getChildren();
        if(children.size() == 1) {
            this.root = children.get(0);
        }
    }

    private void sortSiblingsByStartTime(List<BuildPipelineNode> stages) {
        for(BuildPipelineNode stage : stages) {
            sortSiblingsByStartTime(stage.getChildren());
        }
        stages.sort(new BuildStageComparator());
    }

    private void completeInformation(final List<BuildPipelineNode> stages, final BuildPipelineNode parent) {
        for(int i = 0; i < stages.size(); i++) {
            final BuildPipelineNode stage = stages.get(i);
            final Long endTime = stage.getEndTime();
            if(endTime == null) {
                if(i + 1 < stages.size()) {
                    final BuildPipelineNode sibling = stages.get(i + 1);
                    stage.setEndTime(sibling.getStartTime());
                } else {
                    stage.setEndTime(parent.getEndTime());
                }
            }

            completeInformation(stage.getChildren(), stage);
        }
    }

    private void buildTree(List<BuildStageKey> pathStages, BuildPipelineNode parent, BuildPipelineNode stage) {
        if(pathStages.isEmpty()) {
            return;
        }

        final BuildStageKey buildStageId = pathStages.get(0);
        if(pathStages.size() == 1){
            final BuildPipelineNode child = parent.getChild(buildStageId);
            if (child == null) {
                parent.addChild(stage);
            } else {
                child.updateData(stage);
            }

        } else {
            BuildPipelineNode child = parent.getChild(buildStageId);
            if(child == null) {
                child = BuildPipelineNode.buildStage(buildStageId).build();
                parent.addChild(child);
            }
            buildTree(pathStages.subList(1, pathStages.size()), child, stage);
        }
    }

    private static class BuildStageComparator implements Comparator<BuildPipelineNode> {

        @Override
        public int compare(BuildPipelineNode o1, BuildPipelineNode o2) {
            if(o1.getStartTime() == null || o2.getStartTime() == null) {
                return 0;
            }

            if(o1.getStartTime() < o2.getStartTime()) {
                return -1;
            } else if (o1.getStartTime() > o2.getStartTime()){
                return 1;
            }
            return 0;
        }
    }

}
