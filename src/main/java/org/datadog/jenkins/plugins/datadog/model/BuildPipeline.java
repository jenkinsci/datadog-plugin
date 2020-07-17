package org.datadog.jenkins.plugins.datadog.model;

import static org.datadog.jenkins.plugins.datadog.model.BuildStage.BuildStageKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Jenkins Pipeline.
 * To represent the stages is used an n-ary tree.
 */
public class BuildPipeline {

    private final BuildStage root;
    private final Map<List<BuildStageKey>, BuildStage> stagesByPath;

    public static BuildPipeline newPipeline() {
        return new BuildPipeline(BuildStage.buildStage(BuildStage.buildStageKey("root", "root")).build());
    }

    public BuildPipeline(final BuildStage root) {
        this.root = root;
        this.stagesByPath = new HashMap<>();
    }

    public BuildStage addStage(final List<BuildStageKey> stageRelations, final BuildStage stage) {
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
    public BuildStage buildTree() {
        for(Map.Entry<List<BuildStageKey>, BuildStage> entry : stagesByPath.entrySet()){
            final List<BuildStageKey> pathStages = entry.getKey();
            final BuildStage stage = entry.getValue();
            buildTree(pathStages, root, stage);
        }

        return root;
    }

    private void buildTree(List<BuildStageKey> pathStages, BuildStage parent, BuildStage stage) {
        if(pathStages.isEmpty()) {
            return;
        }

        final BuildStageKey buildStageId = pathStages.get(0);
        if(pathStages.size() == 1){
            final BuildStage child = parent.getChild(buildStageId);
            if (child == null) {
                parent.addChild(stage);
            } else {
                child.updateData(stage);
            }

        } else {
            BuildStage child = parent.getChild(buildStageId);
            if(child == null) {
                child = BuildStage.buildStage(buildStageId).build();
                parent.addChild(child);
            }
            buildTree(pathStages.subList(1, pathStages.size()), child, stage);
        }
    }

}
