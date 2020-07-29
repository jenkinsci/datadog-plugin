package org.datadog.jenkins.plugins.datadog.model;

import datadog.trace.api.DDTags;
import hudson.console.AnnotatedLargeText;
import hudson.model.Result;
import org.datadog.jenkins.plugins.datadog.traces.StepDataManager;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;

import java.io.PrintWriter;
import java.io.StringWriter;
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

    private final BuildPipelineNodeKey key;
    private final List<BuildPipelineNode> children;

    private String id;
    private String name;
    private NodeType type;
    private boolean internal;
    private FlowNode node;

    private Map<String, Object> args = new HashMap<>();
    private Map<String, String> envVars = new HashMap<>();
    private String workspace;
    private String nodeName;
    private String nodeHostname;
    private AnnotatedLargeText logText;

    private StepData stepData;
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
        if(isPipeline(endNode)) {
            this.type = NodeType.PIPELINE;
            this.internal = true;
        } else if(isStage(startNode)){
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
                final StepData.StepEnvVars envVars = stepData.getEnvVars();
                this.envVars = envVars.getEnvVars();

                final StepData.StepFilePath filePath = stepData.getFilePath();
                this.workspace = filePath.getRemote();

                final StepData.StepComputer stepComputer = stepData.getComputer();
                this.nodeName = (!"".equals(stepComputer.getNodeName())) ? stepComputer.getNodeName() : "master";
                this.nodeHostname = stepComputer.getHostName() != null ? stepComputer.getHostName() : "";
            }
        }

        this.logText = getLogText(endNode);
        this.startTime = getTime(startNode);
        this.startTimeMicros = this.startTime * 1000;
        this.endTime = getTime(endNode);
        this.endTimeMicros = this.endTime * 1000;
        this.result = resultForNode(startNode);

        this.errorObj = getErrorObj(endNode);
        if(Result.FAILURE.toString().equals(this.result)){
            this.error = true;
        }
    }

    private AnnotatedLargeText getLogText(FlowNode node) {
        final LogAction logAction = node.getAction(LogAction.class);
        if(logAction != null) {
            return logAction.getLogText();
        }
        return null;
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
            final StepData.StepEnvVars envVars = stepData.getEnvVars();
            this.envVars = envVars.getEnvVars();
            final StepData.StepFilePath filePath = stepData.getFilePath();
            this.workspace = filePath.getRemote();
            final StepData.StepComputer stepComputer = stepData.getComputer();
            this.nodeName = (!"".equals(stepComputer.getNodeName())) ? stepComputer.getNodeName() : "master";
            this.nodeHostname = stepComputer.getHostName() != null ? stepComputer.getHostName() : "";
        }

        this.logText = getLogText(stepNode);
        this.startTime = getTime(stepNode);
        this.startTimeMicros = this.startTime * 1000;
        this.endTime = -1L;
        this.endTimeMicros = this.endTime * 1000;
        this.result = resultForNode(stepNode);
        if(Result.FAILURE.toString().equals(this.result)){
            this.error = true;
        }
    }

    public void addChild(final BuildPipelineNode child) {
        children.add(child);
    }

    public BuildPipelineNodeKey getKey() {
        return key;
    }

    public String getId() {
        return key.id;
    }

    public String getName() {
        return key.name;
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

    public FlowNode getNode() {
        return node;
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

    public StepData getStepData() {
        return stepData;
    }

    public NodeType getType() {
        return type;
    }

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
        this.node = buildNode.node;
        this.stepData = buildNode.stepData;
    }


    private long getTime(FlowNode node) {
        TimingAction time = node.getAction(TimingAction.class);
        if(time != null) {
            return time.getStartTime();
        }
        return -1L;
    }

    private static String resultForNode(FlowNode flowNode) {
        Result result = Result.SUCCESS;

        final ErrorAction errorAction = flowNode.getError();
        final WarningAction warningAction = flowNode.getAction(WarningAction.class);

        if(errorAction != null) {
            result = Result.FAILURE;
        } else if (warningAction != null) {
            result = Result.UNSTABLE;
        }

        return result.toString();
    }

    private boolean isStage(BlockStartNode startNode) {
        return startNode.getAction(LabelAction.class) != null || startNode.getAction(StageAction.class) != null;
    }

    private boolean isPipeline(BlockEndNode endNode) {
        return endNode instanceof FlowEndNode;
    }

    private Throwable getErrorObj(FlowNode node) {
        final ErrorAction errorAction = node.getAction(ErrorAction.class);
        return (errorAction != null) ? errorAction.getError() : null;
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


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BuildPipelineNode{");
        sb.append("key=").append(key);
        sb.append(", children=").append(children);
        sb.append(", node=").append(node);
        sb.append(", stepData=").append(stepData);
        sb.append(", startTime=").append(startTime);
        sb.append(", endTime=").append(endTime);
        sb.append(", result='").append(result).append('\'');
        sb.append(", error=").append(error);
        sb.append('}');
        return sb.toString();
    }

    public enum NodeType {
        PIPELINE, STAGE, STEP
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

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("BuildPipelineNodeKey{");
            sb.append("id='").append(id).append('\'');
            sb.append(", name='").append(name).append('\'');
            sb.append('}');
            return sb.toString();
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
