package org.datadog.jenkins.plugins.datadog.pipeline;

import hudson.EnvVars;
import hudson.console.AnnotatedLargeText;
import hudson.model.Run;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.List;
import java.util.Map.Entry;
import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.Set;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * Pipeline plug-in step for recording time-stamps.
 */
public class DatadogStep extends Step {

    /** Constructor. */
    @DataBoundConstructor
    public DatadogStep() {}

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ExecutionImpl(context);
    }

    private static class ExecutionImpl extends StepExecution {

        ExecutionImpl(StepContext context) {
            super(context);
        }

        private static final long serialVersionUID = 1L;

        /** {@inheritDoc} */
        @Override
        public boolean start() throws Exception {
            StepContext context = getContext();
            BodyInvoker invoker = context.newBodyInvoker().withCallback(BodyExecutionCallback.wrap(context));
            context.get(TaskListener.class).getLogger().println("Starting DatadogStep");
            Run<?, ?> run = context.get(Run.class);
            invoker.withContext(TaskListenerDecorator.merge(context.get(TaskListenerDecorator.class), new DatadogDecorator((WorkflowRun) run)));
            invoker.start();

            TaskListener listener = context.get(TaskListener.class);
            PrintStream stream = listener.getLogger();
//            EnvVars vars = context.get(EnvVars.class);
//            for(Entry<String, String> set : vars.entrySet()){
//                //listener.getLogger().println("DatadogStep: " + set.getKey() + ":" + set.getValue());
//            }
            FlowNode node = context.get(FlowNode.class);
            LogStorage ls = LogStorage.of(node.getExecution().getOwner());
            for(FlowNode n : node.getExecution().iterateEnclosingBlocks(node)){
                BufferedReader r = new BufferedReader(ls.stepLog(node, true).readAll());
                String line;
                while ((line = r.readLine()) != null) {
                    context.get(TaskListener.class).getLogger().println("Buffered: " + line);
                }
            }

            return false;
        }

        /** {@inheritDoc} */
        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
            StepContext context = getContext();
            context.get(TaskListener.class).getLogger().println("Stop DatadogStep");
            context.get(TaskListener.class).getLogger().println(cause.getMessage());

        }
    }

    @Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
    public static class DescriptorImpl extends StepDescriptor {

        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return "DatadogStep";
        }

        /** {@inheritDoc} */
        @Override
        public String getFunctionName() {
            return "datadog";
        }

        /** {@inheritDoc} */
        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }


        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

    }

}