package org.datadog.jenkins.plugins.datadog.listeners;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.QueueListener;
import org.datadog.jenkins.plugins.datadog.model.FlowNodeQueueData;
import org.datadog.jenkins.plugins.datadog.model.QueueInfoAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.logging.Logger;

@Extension
public class DatadogQueueListener extends QueueListener {

    private static final Logger logger = Logger.getLogger(DatadogQueueListener.class.getName());

    @Override
    public void onEnterBuildable(Queue.BuildableItem item) {
        try {
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
            final FlowNode flowNode = placeholderTask.getNode();

            if(flowNode == null) {
                logger.fine("onEnterBuildable PlaceholderTask: " + placeholderTask + ", FlowNode: is null");
                return;
            }

            final Run<?,?> run = runFor(flowNode.getExecution());
            if(run == null) {
                logger.fine("onEnterBuildable FlowNode: " + flowNode + ", run is null.");
                return;
            }

            final QueueInfoAction queueAction = run.getAction(QueueInfoAction.class);
            if(queueAction == null){
                logger.fine("onEnterBuildable: queueAction: is null");
                return;
            }

            final FlowNodeQueueData flowNodeData = queueAction.get(flowNode.getId());
            if(flowNodeData != null) {
                flowNodeData.setEnterBuildable(System.currentTimeMillis());
            } else {
                final FlowNodeQueueData data = new FlowNodeQueueData(flowNode.getId());
                data.setEnterBuildable(System.currentTimeMillis());
                queueAction.put(flowNode.getId(), data);
            }

        } catch (Exception e){
            logger.severe("Error onEnterBuildable: item:" + item + ", exception: " + e);
        }

    }

    @Override
    public void onLeaveBuildable(Queue.BuildableItem item) {
        try {
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
            final FlowNode flowNode = placeholderTask.getNode();
            if(flowNode == null) {
                logger.fine("onLeaveBuildable PlaceholderTask: " + placeholderTask + ", FlowNode: is null");
                return;
            }

            Run<?,?> run = runFor(flowNode.getExecution());
            if(run == null) {
                logger.fine("onLeaveBuildable FlowNode: " + flowNode + ", run is null.");
                return;
            }

            QueueInfoAction queueAction = run.getAction(QueueInfoAction.class);
            if(queueAction == null){
                logger.fine("onLeaveBuildable: queueAction: is null");
                return;
            }

            final FlowNodeQueueData flowNodeData = queueAction.get(flowNode.getId());
            if(flowNodeData != null) {
                flowNodeData.setLeaveBuildable(System.currentTimeMillis());
            }
        } catch (Exception e){
            logger.severe("Error onLeaveBuildable: item:" + item + ", exception: " + e);
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
