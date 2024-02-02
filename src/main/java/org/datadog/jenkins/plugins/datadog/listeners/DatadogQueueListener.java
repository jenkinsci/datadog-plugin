package org.datadog.jenkins.plugins.datadog.listeners;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.QueueListener;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.PipelineQueueInfoAction;
import org.datadog.jenkins.plugins.datadog.model.node.DequeueAction;
import org.datadog.jenkins.plugins.datadog.model.node.EnqueueAction;
import org.datadog.jenkins.plugins.datadog.model.node.QueueInfoAction;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;

@Extension
public class DatadogQueueListener extends QueueListener {

    private static final Logger logger = Logger.getLogger(DatadogQueueListener.class.getName());

    @Override
    public void onEnterBuildable(Queue.BuildableItem item) {
        try {
            if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
                return;
            }

            final Queue.Task task = item.task;
            if(task == null) {
                logger.fine("onEnterBuildable: item: " + item + ", task is null");
                return;
            }

            if(!(task instanceof ExecutorStepExecution.PlaceholderTask)) {
                logger.fine("onEnterBuildable: item: " + item + ", task:" + task + " is not ExecutorStepExecution.PlaceholderTask: " + task.getClass() );
                return;
            }

            final ExecutorStepExecution.PlaceholderTask placeholderTask = (ExecutorStepExecution.PlaceholderTask) task;
            // Use async method to avoid deadlock.
            // It fixes https://github.com/jenkinsci/datadog-plugin/issues/170
            final FlowNode flowNode = getNodeAsync(placeholderTask, 5000);

            if(flowNode == null) {
                logger.fine("onEnterBuildable PlaceholderTask: " + placeholderTask + ", FlowNode: is null");
                return;
            }

            flowNode.addOrReplaceAction(new EnqueueAction(System.nanoTime()));

        } catch (Exception e){
            logger.severe("Error onEnterBuildable: item:" + item + ", exception: " + e);
        }
    }

    @Override
    public void onLeaveBuildable(Queue.BuildableItem item) {
        try {
            if (!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
                return;
            }

            final Queue.Task task = item.task;
            if(task == null) {
                logger.fine("onLeaveBuildable: item: " + item + ", task is null");
                return;
            }

            if(!(task instanceof ExecutorStepExecution.PlaceholderTask)) {
                logger.fine("onLeaveBuildable: item: " + item + ", task:" + task + " is not ExecutorStepExecution.PlaceholderTask: " + task.getClass() );
                return;
            }

            final ExecutorStepExecution.PlaceholderTask placeholderTask = (ExecutorStepExecution.PlaceholderTask) task;
            // Use async method to avoid deadlock.
            // It fixes https://github.com/jenkinsci/datadog-plugin/issues/170
            final FlowNode flowNode = getNodeAsync(placeholderTask, 5000);
            if(flowNode == null) {
                logger.fine("onLeaveBuildable PlaceholderTask: " + placeholderTask + ", FlowNode: is null");
                return;
            }

            EnqueueAction enqueueAction = flowNode.getAction(EnqueueAction.class);
            if (enqueueAction == null) {
                logger.fine("onLeaveBuildable FlowNode: " + flowNode + ", enqueueAction is null.");
                return;
            }

            long queueDurationNanos = System.nanoTime() - enqueueAction.getTimestampNanos();
            DequeueAction queueInfoAction = new DequeueAction(queueDurationNanos);

            // Replace enqueue action with dequeue action in one call to avoid writing to disk twice
            flowNode.replaceActions(QueueInfoAction.class, queueInfoAction);

            propagateQueueTime(flowNode, queueInfoAction);

        } catch (Exception e){
            logger.severe("Error onLeaveBuildable: item:" + item + ", exception: " + e);
        }
    }

    private static void propagateQueueTime(FlowNode flowNode, DequeueAction queueInfoAction) {
        if (flowNode.getDisplayName().contains("Allocate node")) {
            BlockStartNode enclosingNode = DatadogUtilities.getEnclosingStageNode(flowNode);
            if (enclosingNode != null) {
                // propagate queue duration
                enclosingNode.addOrReplaceAction(queueInfoAction);
            }
        }

        List<FlowNode> parents = flowNode.getParents();
        if (parents.size() != 1) {
            return;
        }

        FlowNode parent = parents.iterator().next();
        if (!(parent instanceof FlowStartNode)) {
            // propagate queue time to build level only if dequeued node is the direct child of pipeline node
            return;
        }

        Run<?,?> run = runFor(flowNode.getExecution());
        if (run == null) {
            logger.fine("onLeaveBuildable FlowNode: " + flowNode + ", run is null.");
            return;
        }

        PipelineQueueInfoAction pipelineQueueInfoAction = run.getAction(PipelineQueueInfoAction.class);
        if (pipelineQueueInfoAction == null) {
            logger.fine("onLeaveBuildable FlowNode: " + flowNode + ", pipelineQueueInfoAction is null.");
            return;
        }

        long queueTimeMillis = TimeUnit.NANOSECONDS.toMillis(queueInfoAction.getQueueTimeNanos());
        pipelineQueueInfoAction.setPropagatedQueueTimeMillis(queueTimeMillis);
    }

    /**
     * Gets the FlowNode from the PlaceholderTask asynchronous.
     *
     * This method is needed because there is a deadlock when a job is being executed and
     * the Jenkins instances is killed. After restarting it, the placeholderTask is
     * trying to obtain the FlowNode forever.
     *
     * Related to: [BUG] https://issues.jenkins.io/browse/JENKINS-64688
     * Related to: [BUG] https://github.com/jenkinsci/datadog-plugin/issues/170
     *
     * @param placeholderTask
     * @param timeoutMs
     * @return FlowNode or null
     */
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    private FlowNode getNodeAsync(ExecutorStepExecution.PlaceholderTask placeholderTask, int timeoutMs) {
        try {
            final CompletableFuture<FlowNode> f = new CompletableFuture<>();
            Executors.newCachedThreadPool().submit(() -> f.complete(placeholderTask.getNode()));
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ex){
            logger.fine("Error getNodeAsync for task:"+placeholderTask+", exception: " + ex);
            return null;
        }
    }

    /**
     * Gets the jenkins run object of the specified executing workflow.
     *
     * @param exec execution of a workflow
     * @return jenkins run object of a job
     */
    private static @CheckForNull
    Run<?, ?> runFor(FlowExecution exec) {
        Queue.Executable executable;
        try {
            executable = exec.getOwner().getExecutable();
        } catch (IOException x) {
            return null;
        }
        if (executable instanceof Run) {
            return (Run<?, ?>) executable;
        } else {
            return null;
        }
    }
}
