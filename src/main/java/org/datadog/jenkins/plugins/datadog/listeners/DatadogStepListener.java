package org.datadog.jenkins.plugins.datadog.listeners;

import hudson.Extension;
import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.StepData;
import org.datadog.jenkins.plugins.datadog.traces.StepDataAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.logging.Logger;

@Extension
public class DatadogStepListener implements StepListener {

    private static final Logger logger = Logger.getLogger(DatadogStepListener.class.getName());

    @Override
    public void notifyOfNewStep(@Nonnull Step step, @Nonnull StepContext context) {
        try {
            final Run<?,?> run = context.get(Run.class);
            final StepDataAction stepDataAction = run.getAction(StepDataAction.class);
            if(stepDataAction == null) {
                logger.fine("Unable to store Step data in Run '"+run.getFullDisplayName()+"'. StepDataAction is null");
                return;
            }

            final FlowNode flowNode = context.get(FlowNode.class);
            if(flowNode == null) {
                logger.severe("Unable to store Step data in Run '"+run.getFullDisplayName()+"'. FlowNode is null");
                return;
            }

            if(!(flowNode instanceof StepAtomNode)){
                return;
            }

            final StepData stepData = new StepData(context);
            stepDataAction.put(flowNode, stepData);


            // We use the PipelineNodeInfoAction to propagate
            // the correct node name to the root span (ci.pipeline).

            // Check if the pipeline node info has been stored in previous steps.
            // If so, there is no need to search this information again.
            final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
            if(pipelineNodeInfoAction != null) {
                return;
            }

            // If the first 'Allocate node : Start' flow node
            // is a direct child of the `Start of Pipeline` flow node, the reported node name
            // for this Step belongs also to the `Start of Pipeline` flow node,
            // meaning, it's the node name for the root span (ci.pipeline).

            // Starting from the current flow node (which represents the step),
            // we try to find the first 'Allocate node : Start' node through its parents.
            final FlowNode firstAllocateNodeStart = findFirstAllocateNodeStart(flowNode);
            if(firstAllocateNodeStart == null){
                return;
            }

            // If the parent block from the first 'Allocate node : Start' node is the 'Start of Pipeline' node
            // the worker node where this Step was executed will be the worker node for the pipeline.
            final Iterator<BlockStartNode> blockStartNodes = firstAllocateNodeStart.iterateEnclosingBlocks().iterator();
            if(blockStartNodes.hasNext()) {
                final FlowNode candidate = blockStartNodes.next();
                if("Start of Pipeline".equals(candidate.getDisplayName())) {
                    run.addAction(new PipelineNodeInfoAction(stepData.getNodeName() != null ? stepData.getNodeName() : "master"));
                }
            }

        } catch (Exception ex) {
            logger.severe("Unable to extract Run information of the StepContext. " + ex);
        }
    }

    private FlowNode findFirstAllocateNodeStart(FlowNode current) {
        for(FlowNode block : current.iterateEnclosingBlocks()) {
            if("Allocate node : Start".equalsIgnoreCase(block.getDisplayName())){
                return block;
            }
        }
        return null;
    }
}
