package org.datadog.jenkins.plugins.datadog.util;

import java.util.Collections;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;

/**
 * Computes how long a Pipeline spent paused (e.g. waiting on an {@code input} step), based on the
 * {@link PauseAction}s recorded on its flow nodes.
 */
public final class PauseDurations {

    private PauseDurations() {
    }

    /**
     * @return the total pause time, in milliseconds, of every executed node of the given execution
     */
    public static long forExecution(@CheckForNull FlowExecution execution) {
        if (execution == null) {
            return 0;
        }
        long pause = 0;
        for (FlowNode node : new DepthFirstScanner().allNodes(execution)) {
            pause += forNode(node);
        }
        return pause;
    }

    /**
     * @return the total pause time, in milliseconds, of every executed node inside the given block
     * (nested blocks and parallel branches included)
     */
    public static long forBlock(@Nonnull BlockEndNode<?> endNode) {
        BlockStartNode startNode = endNode.getStartNode();
        long pause = 0;
        // A block has a single entry point, so walking back from its end without going past its start
        // visits exactly the nodes it encloses.
        DepthFirstScanner scanner = new DepthFirstScanner();
        scanner.setup(Collections.singleton(endNode), Collections.singleton(startNode));
        for (FlowNode node : scanner) {
            pause += forNode(node);
        }
        return pause;
    }

    private static long forNode(@Nonnull FlowNode node) {
        if (!NotExecutedNodeAction.isExecuted(node)) {
            return 0;
        }
        return Math.max(0, PauseAction.getPauseDuration(node));
    }
}
