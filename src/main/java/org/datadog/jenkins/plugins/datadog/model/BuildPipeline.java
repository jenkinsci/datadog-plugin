package org.datadog.jenkins.plugins.datadog.model;

import static org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode.BuildPipelineNodeKey;

import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Jenkins Pipeline.
 * The stages are represented using an n-ary tree.
 */
public class BuildPipeline {

    private final Map<List<BuildPipelineNodeKey>, BuildPipelineNode> stagesByPath;
    private BuildPipelineNode root;

    public BuildPipeline() {
        this.stagesByPath = new HashMap<>();
        this.root = new BuildPipelineNode("initial", "initial");
    }

    public BuildPipelineNode add(final FlowNode node) {
        final BuildPipelineNode buildNode = buildPipelineNode(node);
        if(buildNode == null) {
            return null;
        }

        final List<BuildPipelineNodeKey> buildNodeRelations = new ArrayList<>();
        buildNodeRelations.add(buildNode.getKey());
        for (final BlockStartNode startNode : node.iterateEnclosingBlocks()) {
            buildNodeRelations.add(new BuildPipelineNodeKey(startNode.getId(), startNode.getDisplayName()));
        }

        Collections.reverse(buildNodeRelations);
        return stagesByPath.put(buildNodeRelations, buildNode);
    }

    private BuildPipelineNode buildPipelineNode(FlowNode node) {
        if(node instanceof BlockEndNode) {
            return new BuildPipelineNode((BlockEndNode) node);
        } else if(node instanceof StepAtomNode) {
            return new BuildPipelineNode((StepAtomNode) node);
        }
        return null;
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
     * @return the build pipeline tree.
     **/
    public BuildPipelineNode buildTree() {
        for(Map.Entry<List<BuildPipelineNodeKey>, BuildPipelineNode> entry : stagesByPath.entrySet()){
            final List<BuildPipelineNodeKey> pathStages = entry.getKey();
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
        stages.sort(new BuildPipelineNode.BuildPipelineNodeComparator());
    }

    private void completeInformation(final List<BuildPipelineNode> nodes, final BuildPipelineNode parent) {
        for(int i = 0; i < nodes.size(); i++) {
            final BuildPipelineNode node = nodes.get(i);
            final Long endTime = node.getEndTime();
            if(endTime == -1L) {
                if(i + 1 < nodes.size()) {
                    final BuildPipelineNode sibling = nodes.get(i + 1);
                    node.setEndTime(sibling.getStartTime());
                } else {
                    node.setEndTime(parent.getEndTime());
                }
            }

            // Propagate Stage Name to its children
            if(!BuildPipelineNode.NodeType.STAGE.equals(node.getType())) {
                if(BuildPipelineNode.NodeType.STAGE.equals(parent.getType())) {
                    node.setStageName(parent.getName());
                } else if(parent.getStageName() != null){
                    node.setStageName(parent.getStageName());
                }
            }

            // Propagate queue time from "Allocate node" child:
            // If the node is the initial (Start of Pipeline) or is a Stage,
            // we need to propagate the queue time stored in its child node ("Allocate node").
            // This is necessary because the stage/pipeline node does not have the queue time itself,
            // but it's stored in the "Allocate node" which is its child.
            if((node.isInitial() || BuildPipelineNode.NodeType.STAGE.equals(node.getType())) && node.getChildren().size() == 1){
                BuildPipelineNode child = node.getChildren().get(0);
                if(child.getName().contains("Allocate node")) {
                    node.setPropagatedNanosInQueue(child.getNanosInQueue());
                }
            }


            // Propagate worker node name from the executable child node
            // (where the worker node info is available) to its stage.
            if(BuildPipelineNode.NodeType.STAGE.equals(node.getType())) {
                final BuildPipelineNode executableChildNode = searchExecutableChildNode(node);
                if(executableChildNode != null) {
                    node.setPropagatedNodeName(executableChildNode.getNodeName());
                    node.setPropagatedNodeLabels(executableChildNode.getNodeLabels());
                }
            }

            // Propagate error to all parent stages
            if(node.isError() && !parent.isError()) {
                propagateErrorToAllParents(node);
            }

            // Notice we cannot propagate the worker node info
            // to the root span at this point, because this method is executed
            // after the root span is sent. To propagate worker node info
            // to the root span, we use the PipelineNodeInfoAction that is populated
            // in the DatadogStepListener class.

            completeInformation(node.getChildren(), node);
        }
    }

    private void propagateErrorToAllParents(BuildPipelineNode node) {
        for(BuildPipelineNode parent : node.getParents()) {
            propagateErrorToAllParents(parent);
        }
        node.setError(true);
        node.setPropagatedResult("error");
    }

    private BuildPipelineNode searchExecutableChildNode(BuildPipelineNode node) {
        if(!node.isInternal() && BuildPipelineNode.NodeType.STEP.equals(node.getType())){
            return node;
        }else if ("Stage : Start".equalsIgnoreCase(node.getName())) {
            // If we find a "Stage : Start" as child, we need to stop searching
            // because we're changing the Stage, so the executable child node
            // will not belong to the required stage.
            return null;
        } else {
            for(BuildPipelineNode child : node.getChildren()){
                final BuildPipelineNode found = searchExecutableChildNode(child);
                if(found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void buildTree(List<BuildPipelineNodeKey> pathStages, BuildPipelineNode parent, BuildPipelineNode stage) {
        if(pathStages.isEmpty()) {
            return;
        }

        final BuildPipelineNodeKey buildNodeKey = pathStages.get(0);
        if(pathStages.size() == 1){
            final BuildPipelineNode child = parent.getChild(buildNodeKey);
            if (child == null) {
                parent.addChild(stage);
            } else {
                child.updateData(stage);
            }

        } else {
            BuildPipelineNode child = parent.getChild(buildNodeKey);
            if(child == null) {
                child = new BuildPipelineNode(buildNodeKey);
                parent.addChild(child);
            }
            buildTree(pathStages.subList(1, pathStages.size()), child, stage);
        }
    }
}
