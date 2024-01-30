package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.Cause;
import hudson.model.Run;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.StageData;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.json.JsonUtils;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Base class for DatadogTraceBuildLogic and DatadogPipelineBuildLogic
 */
public abstract class DatadogBaseBuildLogic {

    protected static final String HOSTNAME_NONE = "none";
    private static final int MAX_TAG_LENGTH = 5000;
    private static final Logger logger = Logger.getLogger(DatadogBaseBuildLogic.class.getName());

    @Nullable
    public abstract JSONObject toJson(final BuildData buildData, final Run<?,?> run);

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    protected Set<String> getNodeLabels(Run<?,?> run, final String nodeName) {
        try {
            if(run == null){
                return Collections.emptySet();
            }

            // First examine PipelineNodeInfoAction associated with the build.
            // The action is populated in step listener based on environment and executor data available for pipeline steps.
            final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
            if(pipelineNodeInfoAction != null) {
                return pipelineNodeInfoAction.getNodeLabels();
            }

            if(run.getExecutor() != null && run.getExecutor().getOwner() != null) {
                Set<String> nodeLabels = DatadogUtilities.getNodeLabels(run.getExecutor().getOwner());
                if(nodeLabels != null && !nodeLabels.isEmpty()) {
                    return nodeLabels;
                }
            }

            // If there is no labels and the node name is master,
            // we force the label "master".
            if(DatadogUtilities.isMainNode(nodeName)) {
                final Set<String> masterLabels = new HashSet<>();
                masterLabels.add("master");
                return masterLabels;
            }

            return Collections.emptySet();
        } catch (Exception ex) {
            logger.fine("Unable to find node labels: " + ex.getMessage());
            return Collections.emptySet();
        }
    }

    protected long getMillisInQueue(BuildData buildData) {
        // Reported by the Jenkins Queue API.
        // It's not included in the root span duration.
        final long millisInQueue = buildData.getMillisInQueue(-1L);

        // Reported by a child span.
        // It's included in the root span duration.
        final long propagatedMillisInQueue = buildData.getPropagatedMillisInQueue(-1L);
        return Math.max(Math.max(millisInQueue, propagatedMillisInQueue), 0);
    }

    protected String getStageBreakdown(Run run) {
        if (!(run instanceof WorkflowRun)) {
            return null;
        }

        WorkflowRun workflowRun = (WorkflowRun) run;
        FlowExecution execution = workflowRun.getExecution();
        if (execution == null) {
            return null;
        }

        List<FlowNode> currentHeads = execution.getCurrentHeads();
        if (currentHeads == null || currentHeads.isEmpty()) {
            return null;
        }

        final List<StageData> stages = traverseStages(currentHeads);
        Collections.sort(stages);

        final String stagesJson = JsonUtils.toJson(new ArrayList<>(stages));
        if (stagesJson.length() > MAX_TAG_LENGTH) {
            logger.warning("Skipping sending stage to Datadog; stage breakdown is too large");
            return null;
        }

        return stagesJson;
    }

    private List<StageData> traverseStages(List<FlowNode> heads) {
        List<StageData> stages = new ArrayList<>();
        Queue<FlowNode> nodes = new ArrayDeque<>(heads);
        while (!nodes.isEmpty()) {
            FlowNode node = nodes.poll();
            nodes.addAll(node.getParents());

            if (!(node instanceof BlockEndNode)) {
                continue;
            }

            BlockEndNode<?> endNode = (BlockEndNode<?>) node;
            BlockStartNode startNode = endNode.getStartNode();
            if (!DatadogUtilities.isStageNode(startNode)) {
                continue;
            }

            long startTimeMicros = TimeUnit.MILLISECONDS.toMicros(DatadogUtilities.getTimeMillis(startNode));
            long endTimeMicros = TimeUnit.MILLISECONDS.toMicros(DatadogUtilities.getTimeMillis(endNode));
            if (startTimeMicros <= 0 || endTimeMicros <= 0) {
                logger.fine("Skipping stage " + startNode.getDisplayName() + " because it has no time info " +
                        "(start: " + startTimeMicros + ", end: " + endTimeMicros + ")");
                continue;
            }

            StageData stageData = new StageData.Builder()
                    .withName(startNode.getDisplayName())
                    .withStartTimeInMicros(startTimeMicros)
                    .withEndTimeInMicros(endTimeMicros)
                    .build();
            stages.add(stageData);
        }
        return stages;
    }

    // Returns true if the run causes contains a Cause.UserIdCause
    public boolean isTriggeredManually(Run run) {
        final List<Cause> causes = run.getCauses();
        for (Cause cause : causes) {
            if (cause instanceof Cause.UserIdCause) {
                return true;
            }
        }
        return false;
    }

}
