package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.Run;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.audit.DatadogAudit;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipeline;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.CIGlobalTagsAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;

/**
 * Base class with shared code for DatadogTracePipelineLogic and DatadogWebhookPipelineLogic
 */
public abstract class DatadogBasePipelineLogic {

    protected static final String CI_PROVIDER = "jenkins";
    protected static final String HOSTNAME_NONE = "none";
    private static final Logger logger = Logger.getLogger(DatadogBasePipelineLogic.class.getName());

    @Nonnull
    public abstract Collection<JSONObject> execute(FlowNode flowNode, Run<?, ?> run);

    protected BuildPipelineNode buildPipelineTree(FlowEndNode flowEndNode) {

        final BuildPipeline pipeline = new BuildPipeline();

        // As this logic is evaluated in the last node of the graph,
        // getCurrentHeads() method returns all nodes as a plain list.
        final List<FlowNode> currentHeads = flowEndNode.getExecution().getCurrentHeads();

        // Provided that plain list of nodes, the DepthFirstScanner algorithm
        // is used to visit efficiently every node in form of a DAG.
        final DepthFirstScanner scanner = new DepthFirstScanner();
        scanner.setup(currentHeads);

        // Every found flow node of the DAG is added to the BuildPipeline instance.
        scanner.forEach(pipeline::add);

        return pipeline.buildTree(); // returns the root node
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    protected Set<String> getNodeLabels(Run run, BuildPipelineNode current, String nodeName) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
        if (current.getNodeLabels() != null && !current.getNodeLabels().isEmpty()) {
            return current.getNodeLabels();
        } else if (pipelineNodeInfoAction != null && !pipelineNodeInfoAction.getNodeLabels().isEmpty()) {
            return pipelineNodeInfoAction.getNodeLabels();
        }

        if (run.getExecutor() != null && run.getExecutor().getOwner() != null) {
            Set<String> nodeLabels = DatadogUtilities.getNodeLabels(run.getExecutor().getOwner());
            if (nodeLabels != null && !nodeLabels.isEmpty()) {
                return nodeLabels;
            }
        }

        // If there is no labels and the node name is master,
        // we force the label "master".
        if (DatadogUtilities.isMainNode(nodeName)) {
            final Set<String> masterLabels = new HashSet<>();
            masterLabels.add("master");
            return masterLabels;
        }

        return Collections.emptySet();
    }

    protected String getNodeName(Run<?, ?> run, BuildPipelineNode current, BuildData buildData) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);

        if(current.getNodeName() != null) {
            return current.getNodeName();
        } else if (pipelineNodeInfoAction != null) {
            return pipelineNodeInfoAction.getNodeName();
        }

        return buildData.getNodeName("");
    }

    protected String getNodeHostname(Run<?, ?> run, BuildPipelineNode current) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
        if(current.getNodeHostname() != null) {
            return current.getNodeHostname();
        } else if (pipelineNodeInfoAction != null) {
            return pipelineNodeInfoAction.getNodeHostname();
        }
        return null;
    }

    protected boolean isTraceable(BuildPipelineNode node) {
        if (node.getStartTimeMicros() == -1L) {
            logger.severe("Unable to send trace of node: " + node.getName() + ". Start Time is not set");
            return false;
        }

        if(node.getEndTimeMicros() == -1L) {
            logger.severe("Unable to send trace of node: " + node.getName() + ". End Time is not set");
            return false;
        }

        if(node.isInternal()){
            logger.fine("Node: " + node.getName() + " is Jenkins internal. We skip it.");
            return false;
        }

        return true;
    }

    protected void updateCIGlobalTags(Run run) {
        long start = System.currentTimeMillis();
        try {
            final CIGlobalTagsAction ciGlobalTagsAction = run.getAction(CIGlobalTagsAction.class);
            if(ciGlobalTagsAction == null) {
                return;
            }

            final Map<String, String> tags = TagsUtil.convertTagsToMapSingleValues(DatadogUtilities.getTagsFromPipelineAction(run));
            ciGlobalTagsAction.putAll(tags);
        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("DatadogTracePipelineLogic.updateCIGlobalTags", start, end);
        }
    }

    protected String buildOperationName(BuildPipelineNode current) {
        return CI_PROVIDER + "." + current.getType().name().toLowerCase() + ((current.isInternal()) ? ".internal" : "");
    }
}
