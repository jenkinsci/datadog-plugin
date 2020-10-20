package org.datadog.jenkins.plugins.datadog.model;

import datadog.trace.api.DDId;
import hudson.console.AnnotatedLargeText;
import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.traces.GeneratedSpanIdAction;
import org.datadog.jenkins.plugins.datadog.traces.StepDataAction;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Represent a stage of the Jenkins Pipeline.
 */
public class BuildPipelineNode {

    private static final Logger logger = Logger.getLogger(BuildPipelineNode.class.getName());

    public enum NodeType {
        PIPELINE("ci.pipeline", "pipeline"),
        STAGE("ci.stage", "stage"),
        STEP("ci.job", "job");

        private final String tagName;
        private final String buildLevel;

        NodeType(final String tagName, final String buildLevel) {
            this.tagName = tagName;
            this.buildLevel = buildLevel;
        }

        public String getTagName() {
            return tagName;
        }

        public String getBuildLevel() {
            return buildLevel;
        }
    }

    private final BuildPipelineNodeKey key;
    private final List<BuildPipelineNode> parents;
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

    // Flag that indicates if the node must be marked as error.
    private boolean error;
    // Throwable of the node.
    // Although the error flag was true, this can be null.
    private Throwable errorObj;

    //OpenTracing
    private DDId generatedSpanId;

    public BuildPipelineNode(final String id, final String name) {
        this(new BuildPipelineNodeKey(id, name));
    }

    public BuildPipelineNode(final BuildPipelineNodeKey key) {
        this.key = key;
        this.parents = new ArrayList<>();
        this.children = new ArrayList<>();
        this.id = key.id;
        this.name = key.name;
    }

    public BuildPipelineNode(final BlockEndNode endNode) {
        final BlockStartNode startNode = endNode.getStartNode();
        this.key = new BuildPipelineNodeKey(startNode.getId(), startNode.getDisplayName());
        this.parents = new ArrayList<>();
        this.children = new ArrayList<>();

        this.id = startNode.getId();
        this.name = startNode.getDisplayName();
        if(DatadogUtilities.isPipelineNode(endNode)) {
            // The pipeline node must be treated as Step.
            // Only root span must have ci.pipeline.* tags.
            // https://datadoghq.atlassian.net/browse/CIAPP-190
            // The pipeline node is not the root span.
            // In Jenkins, the build span is the root span, and
            // the pipeline node span is a child of the build span.
            this.type = NodeType.STEP;
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
            final StepData stepData = getStepData(endNode);
            if(stepData != null) {
                this.envVars = stepData.getEnvVars();
                this.workspace = stepData.getWorkspace();
                this.nodeName = stepData.getNodeName();
                this.nodeHostname = stepData.getNodeHostname();
                this.generatedSpanId = getGeneratedSpanId(endNode);
            }

        }

        this.logText = getLogText(endNode);
        this.startTime = getTime(startNode);
        this.startTimeMicros = this.startTime * 1000;
        this.endTime = getTime(endNode);
        this.endTimeMicros = this.endTime * 1000;
        this.result = DatadogUtilities.getResultTag(startNode);
        this.errorObj = getErrorObj(endNode);
        if("error".equalsIgnoreCase(this.result)){
            this.error = true;
        }
    }

    private DDId getGeneratedSpanId(final FlowNode node) {
        GeneratedSpanIdAction action = node.getAction(GeneratedSpanIdAction.class);
        if(action == null && node instanceof BlockEndNode) {
            action = ((BlockEndNode) node).getStartNode().getAction(GeneratedSpanIdAction.class);
        }

        return (action!=null) ? action.getDDSpanId() : null;
    }

    public BuildPipelineNode(final StepAtomNode stepNode) {
        this.key = new BuildPipelineNodeKey(stepNode.getId(), stepNode.getDisplayName());
        this.parents = new ArrayList<>();
        this.children = new ArrayList<>();
        this.internal = false;
        this.id = stepNode.getId();
        this.name = stepNode.getDisplayName();
        this.type = NodeType.STEP;
        this.args = ArgumentsAction.getFilteredArguments(stepNode);

        final StepData stepData = getStepData(stepNode);
        if(stepData != null) {
            this.envVars = stepData.getEnvVars();
            this.workspace = stepData.getWorkspace();
            this.nodeName = stepData.getNodeName();
            this.nodeHostname = stepData.getNodeHostname();
            this.generatedSpanId = getGeneratedSpanId(stepNode);
        }

        this.logText = getLogText(stepNode);
        this.startTime = getTime(stepNode);
        this.startTimeMicros = this.startTime * 1000;
        this.endTime = -1L;
        this.endTimeMicros = this.endTime * 1000;
        this.result = DatadogUtilities.getResultTag(stepNode);
        this.errorObj = getErrorObj(stepNode);
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

    public DDId getGeneratedSpanId() {
        return generatedSpanId;
    }

    public boolean isError() {
        return error;
    }

    public List<BuildPipelineNode> getParents(){ return parents; }

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
        this.generatedSpanId = buildNode.generatedSpanId;
    }

    public void addChild(final BuildPipelineNode child) {
        children.add(child);
    }

    public void addParent(BuildPipelineNode parent) {
        parents.add(parent);
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


    /**
     * Returns the startTime of a certain {@code FlowNode}, if it has time information.
     * @param flowNode
     * @return startTime of the flowNode in milliseconds.
     */
    private static long getTime(FlowNode flowNode) {
        TimingAction time = flowNode.getAction(TimingAction.class);
        if(time != null) {
            return time.getStartTime();
        }
        return -1L;
    }

    /**
     * Returns the accessor to the logs of a certain {@code FlowNode}, if it has logs.
     * @param flowNode
     * @return accessor to the flowNode logs.
     */
    private static AnnotatedLargeText getLogText(FlowNode flowNode) {
        final LogAction logAction = flowNode.getAction(LogAction.class);
        if(logAction != null) {
            return logAction.getLogText();
        }
        return null;
    }


    /**
     * Returns the {@code Throwable} of a certain {@code FlowNode}, if it has errors.
     * @param flowNode
     * @return throwable associated with a certain flowNode.
     */
    private static Throwable getErrorObj(FlowNode flowNode) {
        final ErrorAction errorAction = flowNode.getAction(ErrorAction.class);
        return (errorAction != null) ? errorAction.getError() : null;
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private StepData getStepData(final FlowNode node) {
        final Run<?, ?> run = getRun(node);
        if(run == null) {
            logger.fine("Unable to get StepData from node '"+node.getDisplayName()+"'. Run is null");
            return null;
        }

        final StepDataAction stepDataAction = run.getAction(StepDataAction.class);
        if(stepDataAction == null) {
            logger.fine("Unable to get StepData from node '"+node.getDisplayName()+"'. StepDataAction is null");
            return null;
        }

        return stepDataAction.get(((StepNode) node).getDescriptor());
    }

    private Run<?, ?> getRun(final FlowNode node) {
        if(node == null ||  node.getExecution() == null || node.getExecution().getOwner() == null) {
            return null;
        }

        try {
            return (Run<?, ?>) node.getExecution().getOwner().getExecutable();
        } catch (Exception e){
            return null;
        }
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


    static class BuildPipelineNodeComparator implements Comparator<BuildPipelineNode>, Serializable {

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
