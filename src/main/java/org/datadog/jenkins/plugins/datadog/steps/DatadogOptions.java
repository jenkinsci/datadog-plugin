package org.datadog.jenkins.plugins.datadog.steps;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.YesNoMaybe;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.logs.DatadogTaskListenerDecorator;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Pipeline plug-in step for configuring Datadog monitoring options.
 */
public class DatadogOptions extends Step implements Serializable {

    private static final long serialVersionUID = 1L;
    private boolean collectLogs = false;
    private List<String> tags = new ArrayList<>();
    private TestOptimization testOptimization;

    /** Constructor. */
    @DataBoundConstructor
    public DatadogOptions() {
    }

    public boolean getCollectLogs() {
        return collectLogs;
    }

    @DataBoundSetter
    public void setCollectLogs(boolean collectLogs) {
        this.collectLogs = collectLogs;
    }

    public TestOptimization getTestOptimization() {
        return testOptimization;
    }

    @DataBoundSetter
    public void setTestOptimization(TestOptimization testOptimization) {
        this.testOptimization = testOptimization;
    }

    /**
     * @deprecated Use {@link #getTestOptimization()} instead.
     */
    @Deprecated
    public TestOptimization getTestVisibility() {
        return testOptimization;
    }

    /**
     * @deprecated Use {@link #setTestOptimization(TestOptimization)} instead.
     */
    @Deprecated
    @DataBoundSetter
    public void setTestVisibility(TestOptimization testOptimization) {
        this.testOptimization = testOptimization;
    }

    public List<String> getTags() {
        return tags;
    }

    @DataBoundSetter
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public StepExecution start(StepContext context) {
        DatadogPipelineAction action = new DatadogPipelineAction(this.collectLogs, this.tags, this.testOptimization);
        return new ExecutionImpl(context, action);
    }

    private static class ExecutionImpl extends StepExecution {

        private static final long serialVersionUID = 1L;
        private DatadogPipelineAction action;

        ExecutionImpl(StepContext context, DatadogPipelineAction action) {
            super(context);
            this.action = action;
        }

        /** {@inheritDoc} */
        @Override
        public boolean start() throws Exception {
            StepContext context = getContext();
            TaskListener listener = context.get(TaskListener.class);
            assert listener != null;
            PrintStream taskLogger = listener.getLogger();
            Run<?, ?> run = context.get(Run.class);
            if (!(run instanceof WorkflowRun)) {
                // Really not expected
                return false;
            }
            WorkflowRun workflowRun = (WorkflowRun) run;

            FlowNode optionsNode = context.get(FlowNode.class);
            BlockStartNode stageNode = DatadogUtilities.getEnclosingStageNode(optionsNode);
            if (stageNode != null) {
                stageNode.addOrReplaceAction(action);
            } else {
                run.addOrReplaceAction(action);
            }

            BodyInvoker invoker = context.newBodyInvoker().withCallback(BodyExecutionCallback.wrap(context));
            if (this.action.isCollectLogs()) {
                if (DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildLogs()) {
                    taskLogger.println("[Datadog] Logging is already enabled globally, you do not need to specify 'collectLogs: true'");
                } else {
                    // not checking DatadogUtilities#isJobTracked here:
                    // if Datadog step was added to the job, we assume it should be tracked
                    invoker.withContext(TaskListenerDecorator.merge(
                            context.get(TaskListenerDecorator.class), new DatadogTaskListenerDecorator(workflowRun))
                    );
                }
            }
            invoker.start();
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
            return "DatadogOptions";
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
