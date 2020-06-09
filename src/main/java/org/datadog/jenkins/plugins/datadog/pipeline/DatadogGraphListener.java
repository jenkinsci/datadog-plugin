package org.datadog.jenkins.plugins.datadog.pipeline;

import hudson.Extension;
import java.io.IOException;
import java.io.PrintStream;
import hudson.model.Action;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

@Extension
public class DatadogGraphListener implements GraphListener {
    @Override
    public void onNewHead(FlowNode node) {
//        try {
//            PrintStream logger = node.getExecution().getOwner().getListener().getLogger();
//            logger.println("[onNewHead] Found a new node " + node.toString());
//            logger.println("[onNewHead] Class is " + node.getClass() + ", interfaces: " + node.getClass().getInterfaces().toString() + ", super: " + node.getClass().getSuperclass());
//            logger.println("[onNewHead] DisplayName: " + node.getDisplayName() + ", DisplayFunctionName: " + node.getDisplayFunctionName());
//            logger.println("[onNewHead] Actions are: ");
//            for(Action a : node.getActions()){
//                logger.println("[onNewHead] - " + a.getClass().getCanonicalName() + ":" + a.getDisplayName());
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    private static boolean isStage(FlowNode node) {
        if (node instanceof StepAtomNode) {
            // This filters out labelled steps, such as `sh(script: "echo 'hello'", label: 'echo')`
            return false;
        }
        return node != null && ((node.getAction(StageAction.class) != null)
                || (node.getAction(LabelAction.class) != null && node.getAction(ThreadNameAction.class) == null));
    }
}

