package org.datadog.jenkins.plugins.datadog.model;

import hudson.console.AnnotatedLargeText;
import hudson.model.Node;
import hudson.model.Result;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.traces.StepDataManager;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represent a stage of the Jenkins Pipeline.
 */
public class BuildPipelineNode {

    public enum NodeType {
        PIPELINE("pipeline"), STAGE("stage"), STEP("step", "job");

        private final String name;
        private final String normalizedName;

        NodeType(final String name) {
            this.name = name;
            this.normalizedName = name;
        }

        NodeType(final String name, final String normalizedName) {
            this.name = name;
            this.normalizedName = normalizedName;
        }

        public String getName() {
            return name;
        }

        public String getNormalizedName() {
            return normalizedName;
        }
    }

    private final BuildPipelineNodeKey key;
    private final List<BuildPipelineNode> children;
    private final String id;
    private final String name;

    private NodeType type;
    private boolean internal;
    private Map<String, Object> args = new HashMap<>();
    private Map<String, String> envVars = new HashMap<>();
    private String workspace;
    private String nodeName;
    private String nodeHostname;
    private AnnotatedLargeText logText;
    private long startTime;
    private long startTimeMicros;
    private long endTime;
    private long endTimeMicros;
    private String result;
    private boolean error;
    private Throwable errorObj;

    public BuildPipelineNode(final String id, final String name) {
        this(new BuildPipelineNodeKey(id, name));
    }

    public BuildPipelineNode(final BuildPipelineNodeKey key) {
        this.key = key;
        this.children = new ArrayList<>();
        this.id = key.id;
        this.name = key.name;
    }

    public BuildPipelineNode(final BlockEndNode endNode) {
        final BlockStartNode startNode = endNode.getStartNode();
        this.key = new BuildPipelineNodeKey(startNode.getId(), startNode.getDisplayName());
        this.children = new ArrayList<>();

        this.id = startNode.getId();
        this.name = startNode.getDisplayName();
        if(DatadogUtilities.isPipelineNode(endNode)) {
            this.type = NodeType.PIPELINE;
            this.internal = true;
        } else if(DatadogUtilities.isStageNode(startNode)){
            this.type = NodeType.STAGE;
            this.internal = false;
        } else{
            this.type = NodeType.STEP;
            this.internal = true;
        }

        this.args = ArgumentsAction.getFilteredArguments(startNode);

        if(endNode instanceof StepNode){
            final StepData stepData = StepDataManager.get().remove(((StepNode)endNode).getDescriptor());
            if(stepData != null) {
                this.envVars = stepData.getEnvVars();
                this.workspace = stepData.getWorkspace();
                this.nodeName = stepData.getNodeName();
                this.nodeHostname = stepData.getNodeHostname();
            }
        }

        this.logText = DatadogUtilities.getLogText(endNode);
        this.startTime = DatadogUtilities.getTime(startNode);
        this.startTimeMicros = this.startTime * 1000;
        this.endTime = DatadogUtilities.getTime(endNode);
        this.endTimeMicros = this.endTime * 1000;
        this.result = DatadogUtilities.getResultTag(startNode);
        this.errorObj = DatadogUtilities.getErrorObj(endNode);
        if("error".equalsIgnoreCase(this.result)){
            this.error = true;
        }
    }

    public BuildPipelineNode(final StepAtomNode stepNode) {
        this.key = new BuildPipelineNodeKey(stepNode.getId(), stepNode.getDisplayName());
        this.children = new ArrayList<>();
        this.internal = false;
        this.id = stepNode.getId();
        this.name = stepNode.getDisplayName();
        this.type = NodeType.STEP;
        this.args = ArgumentsAction.getFilteredArguments(stepNode);

        final StepData stepData = StepDataManager.get().remove(stepNode.getDescriptor());
        if(stepData != null) {
            this.envVars = stepData.getEnvVars();
            this.workspace = stepData.getWorkspace();
            this.nodeName = stepData.getNodeName();
            this.nodeHostname = stepData.getNodeHostname();
        }

        this.logText = DatadogUtilities.getLogText(stepNode);
        this.startTime = DatadogUtilities.getTime(stepNode);
        this.startTimeMicros = this.startTime * 1000;
        this.endTime = -1L;
        this.endTimeMicros = this.endTime * 1000;
        this.result = DatadogUtilities.getResultTag(stepNode);
        this.errorObj = DatadogUtilities.getErrorObj(stepNode);
        if("error".equalsIgnoreCase(this.result)){
            this.error = true;
        }
    }

    public BuildPipelineNodeKey getKey() {
        return key;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isInternal() {
        return internal;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public Map<String, String> getEnvVars() {
        return envVars;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getNodeHostname() {
        return nodeHostname;
    }

    public AnnotatedLargeText getLogText() {
        return logText;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getStartTimeMicros() {
        return startTimeMicros;
    }

    public long getEndTimeMicros() {
        return endTimeMicros;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
        this.endTimeMicros = this.endTime * 1000;
    }

    public String getResult() {
        return result;
    }

    public Throwable getErrorObj() {
        return errorObj;
    }

    public boolean isError() {
        return error;
    }

    public List<BuildPipelineNode> getChildren() {
        return children;
    }

    public BuildPipelineNode getChild(final BuildPipelineNodeKey id) {
        if(children.isEmpty()) {
            return null;
        }

        for(final BuildPipelineNode child : children) {
            if(id.equals(child.getKey())){
                return child;
            }
        }

        return null;
    }

    public NodeType getType() {
        return type;
    }

    // Used during the tree is being built in BuildPipeline class.
    public void updateData(final BuildPipelineNode buildNode) {
        this.type = buildNode.type;
        this.internal = buildNode.internal;
        this.args = buildNode.args;
        this.envVars = buildNode.envVars;
        this.workspace = buildNode.workspace;
        this.nodeName = buildNode.nodeName;
        this.nodeHostname = buildNode.nodeHostname;
        this.logText = buildNode.logText;
        this.startTime = buildNode.startTime;
        this.startTimeMicros = buildNode.startTimeMicros;
        this.endTime = buildNode.endTime;
        this.endTimeMicros = buildNode.endTimeMicros;
        this.result = buildNode.result;
        this.error = buildNode.error;
        this.errorObj = buildNode.errorObj;
    }

    public void addChild(final BuildPipelineNode child) {
        children.add(child);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildPipelineNode that = (BuildPipelineNode) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }


    public static class BuildPipelineNodeKey {
        private final String id;
        private final String name;

        public BuildPipelineNodeKey(final String stageId, final String stageName) {
            this.id = stageId;
            this.name = stageName;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BuildPipelineNodeKey that = (BuildPipelineNodeKey) o;
            return Objects.equals(id, that.id) &&
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }
    }


    static class BuildPipelineNodeComparator implements Comparator<BuildPipelineNode> {

        @Override
        public int compare(BuildPipelineNode o1, BuildPipelineNode o2) {
            if(o1.getStartTime() == -1L || o2.getStartTime() == -1L) {
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
