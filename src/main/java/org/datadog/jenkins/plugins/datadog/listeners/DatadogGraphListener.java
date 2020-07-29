/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package org.datadog.jenkins.plugins.datadog.listeners;

import static org.datadog.jenkins.plugins.datadog.model.StepData.StepComputer;
import static org.datadog.jenkins.plugins.datadog.model.StepData.StepEnvVars;

import datadog.trace.api.DDTags;
import hudson.Extension;
import hudson.console.AnnotatedLargeText;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.logs.DatadogOutputStream;
import org.datadog.jenkins.plugins.datadog.logs.DatadogWriter;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.StepData;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTracePipelineLogic;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A GraphListener implementation which computes timing information
 * for the various stages in a pipeline.
 */
@Extension
public class DatadogGraphListener implements GraphListener {

    private static final Logger logger = Logger.getLogger(DatadogGraphListener.class.getName());

    @Override
    public void onNewHead(FlowNode flowNode) {
        //APM Traces
        getTracePipelineLogic().onNewHead(runFor(flowNode.getExecution()), flowNode);

        if (!isMonitored(flowNode)) {
            return;
        }

        DatadogClient client = ClientFactory.getClient();
        if (client == null){
            return;
        }
        StepEndNode endNode = (StepEndNode) flowNode;
        StepStartNode startNode = endNode.getStartNode();
        int stageDepth = 0;
        String directParentName = null;
        for (BlockStartNode node : startNode.iterateEnclosingBlocks()) {
            if (isStageNode(node)) {
                if(directParentName == null){
                    directParentName = getStageName(node);
                }
                stageDepth++;
            }
        }
        if(directParentName == null){
            directParentName = "root";
        }
        WorkflowRun run = getRun(flowNode);
        if(run == null){
            return;
        }

        BuildData buildData = null;
        try {
            buildData = new BuildData(run, flowNode.getExecution().getOwner().getListener());
            String hostname = buildData.getHostname("");
            Map<String, Set<String>> tags = buildData.getTags();
            TagsUtil.addTagToTags(tags, "stage_name", getStageName(startNode));
            TagsUtil.addTagToTags(tags, "parent_stage_name", directParentName);
            TagsUtil.addTagToTags(tags, "stage_depth", String.valueOf(stageDepth));
            tags.remove("result"); // Jenkins sometimes consider the build has a result even though it's still running.
                                        // Stage metrics should never report a result.
            client.gauge("jenkins.job.stage_duration", getTime(startNode, endNode), hostname, tags);
        } catch (IOException | InterruptedException e) {
            DatadogUtilities.severe(logger, e, "Unable to submit the stage duration metric for " + getStageName(startNode));
        }
    }

/*    private void sendPipelineTraces(FlowEndNode flowEndNode) {
        final BuildPipeline pipeline = new BuildPipeline();
        final DepthFirstScanner scanner = new DepthFirstScanner();
        scanner.setup(flowEndNode.getExecution().getCurrentHeads());
        scanner.forEach(flowNode -> {
            if(!(flowNode instanceof BlockEndNode) && !(flowNode instanceof StepAtomNode)) {
                return;
            }

            final String id;
            final String name;
            final Long startTime;
            Long endTime = null;
            final String result;
            if(flowNode instanceof BlockEndNode) {
                final BlockEndNode blockEndNode= (BlockEndNode) flowNode;
                final BlockStartNode blockStartNode = blockEndNode.getStartNode();
                id = blockStartNode.getId();
                name = blockStartNode.getDisplayName();
                startTime = getTime(blockStartNode);
                endTime = getTime(blockEndNode);
                result = resultForNode(blockStartNode);
            } else {
                final StepAtomNode stepAtomNode = (StepAtomNode) flowNode;
                id = stepAtomNode.getId();
                name = stepAtomNode.getDisplayName();
                startTime = getTime(stepAtomNode);
                result = resultForNode(stepAtomNode);
            }

            final BuildStageBuilder stageBuilder = BuildPipelineNode.buildStage(id, name)
                    .withStartTime(startTime)
                    .withEndTime(endTime)
                    .withFlowNode(flowNode)
                    .withResult(result);

            if(flowNode instanceof StepNode) {
                stageBuilder.withStepData(StepDataManager.get().remove(((StepNode) flowNode).getDescriptor()));
            }

            final BuildPipelineNode stage = stageBuilder.build();

            final List<BuildPipelineNodeKey> stageRelations = new ArrayList<>();
            stageRelations.add(stage.getKey());
            for (BlockStartNode node : flowNode.iterateEnclosingBlocks()) {
                stageRelations.add(BuildPipelineNode.buildStageKey(node.getId(), getStageName(node)));
            }

            Collections.reverse(stageRelations); //This is necessary cause Jenkins returns relations inside-out.
            pipeline.addStage(stageRelations, stage);
        });

        final BuildSpanAction buildSpanAction = buildTraceActionFor(flowEndNode.getExecution());
        if(buildSpanAction == null){
            return;
        }

        final DatadogClient client = ClientFactory.getClient();
        if (client == null){
            return;
        }

        final Tracer tracer = client.tracer();
        final SpanContext buildSpanContext = tracer.extract(Format.Builtin.TEXT_MAP, new BuildTextMapAdapter(buildSpanAction.getBuildSpanPropatation()));
        final BuildPipelineNode pipelineNode = pipeline.buildTree();
        sendPipelineNodeTrace(tracer, pipelineNode, buildSpanContext);
    }*/

    private void sendPipelineNodeTrace(final Tracer tracer, final BuildPipelineNode current, final SpanContext parentSpanContext) {
        Span span = null;

        if(isTraceable(current)) {
            Long startTime = current.getStartTime();
            if(startTime == null) {
                logger.severe("Unable to send traces for node '"+current.getName()+"'. Its startTime is null");
                return;
            }

            final Tracer.SpanBuilder spanBuilder = tracer
                    .buildSpan("jenkins.pipeline")
                    .withStartTimestamp(startTime * 1000);

            if(parentSpanContext != null) {
                spanBuilder.asChildOf(parentSpanContext);
            }

            span = spanBuilder.start();
            span.setTag(DDTags.SERVICE_NAME, "jenkins");
            span.setTag(DDTags.RESOURCE_NAME, current.getName());
            span.setTag(DDTags.SPAN_TYPE, "ci");
            span.setTag(DDTags.LANGUAGE_TAG_KEY, "");
            span.setTag("ci.provider", "jenkins");
            span.setTag("job.id", current.getId());
            span.setTag("job.name", current.getName());
            span.setTag("jenkins.result", current.getResult());
            span.setTag("error", current.isError());

            final StepData stepData = current.getStepData();
            if(stepData != null) {
                final StepEnvVars envVars = stepData.getEnvVars();
                span.setTag("repository.branch", envVars.get("GIT_BRANCH"));
                span.setTag("repository.commit", envVars.get("GIT_COMMIT"));
                span.setTag("repository.url", envVars.get("GIT_URL"));
                span.setTag("job.url", envVars.get("BUILD_URL"));
                span.setTag("user.principal", envVars.get("USER"));

                final StepData.StepFilePath filePath = stepData.getFilePath();
                span.setTag("job.workspace", filePath.getRemote());

                final StepComputer stepComputer = stepData.getComputer();
                span.setTag("node.name", (!"".equals(stepComputer.getNodeName())) ? stepComputer.getNodeName() : "master");
                span.setTag("node.hostname", stepComputer.getHostName() != null ? stepComputer.getHostName() : "");
            }

            //Arguments
            final FlowNode node = current.getNode();
            final Map<String, Object> arguments = new HashMap<>(ArgumentsAction.getFilteredArguments(node));
            if(node instanceof BlockEndNode) {
                BlockStartNode startNode = ((BlockEndNode) node).getStartNode();
                Map<String, Object> argumentsFromStart = ArgumentsAction.getFilteredArguments(startNode);
                arguments.putAll(argumentsFromStart);
            }
            for(Map.Entry<String, Object> entry : arguments.entrySet()) {
                span.setTag("jenkins.step.args."+entry.getKey(), String.valueOf(entry.getValue()));

                if("script".equals(entry.getKey())){
                    span.setTag("job.script", String.valueOf(entry.getValue()));
                }
            }

            //Error
            final ErrorAction errorAction = node.getAction(ErrorAction.class);
            if(errorAction != null) {
                final Throwable error = errorAction.getError();
                span.setTag(DDTags.ERROR_MSG, error.getMessage());
                span.setTag(DDTags.ERROR_TYPE, error.getClass().getName());

                final StringWriter errorString = new StringWriter();
                error.printStackTrace(new PrintWriter(errorString));
                span.setTag(DDTags.ERROR_STACK, errorString.toString());
            }

            //Logs
            final LogAction logAction = node.getAction(LogAction.class);
            if(logAction != null) {
                final AnnotatedLargeText<? extends FlowNode> logText = logAction.getLogText();
                final WorkflowRun run = getRun(current.getNode());
                if(run != null) {
                    final DatadogWriter writer = new DatadogWriter(run, run.getCharset(), span.context());
                    final OutputStream out = new DatadogOutputStream(writer);

                    try {
                        logText.writeLogTo(0, out);
                    } catch (Exception e) {
                        System.out.println("--------- LogAction ex: " + e);
                    }
                }

            }
        }

        for(final BuildPipelineNode child : current.getChildren()) {
            sendPipelineNodeTrace(tracer, child, (span != null) ? span.context() : parentSpanContext);
        }

        if(span != null) {
            Long endTime = current.getEndTime();
            if(endTime == null) {
                logger.severe("Unable to send traces for node '"+current.getName()+"'. Its endTime is null");
                return;
            }

            span.finish(endTime * 1000);
        }
    }

    private boolean isTraceable(BuildPipelineNode current) {
        final FlowNode flowNode = current.getNode();

        if(flowNode instanceof BlockEndNode){
            final BlockStartNode startNode = ((BlockEndNode) flowNode).getStartNode();
            return startNode.getAction(LabelAction.class) != null || startNode.getAction(StageAction.class) != null;
        } else {
            return flowNode instanceof StepAtomNode;
        }
    }


    private boolean isMonitored(FlowNode flowNode) {
        // Filter the node out if it is not the end of step
        // Timing information is only available once the step has completed.
        if (!(flowNode instanceof StepEndNode)) {
            return false;
        }

        // Filter the node if the job has been blacklisted from the Datadog plugin configuration.
        WorkflowRun run = getRun(flowNode);
        if (run == null || !DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
            return false;
        }

        // Filter the node out if it is not the end of a stage.
        // The plugin only monitors timing information of stages
        if(!isStageNode(((StepEndNode) flowNode).getStartNode())){
            return false;
        }

        // Finally return true as this node is the end of a monitored stage.
        return true;
    }

    @CheckForNull
    private WorkflowRun getRun(@Nonnull FlowNode flowNode) {
        Queue.Executable exec;
        try {
            exec = flowNode.getExecution().getOwner().getExecutable();
        } catch (IOException e) {
            // Ignore the error, that step cannot be monitored.
            return null;
        }

        if (exec instanceof WorkflowRun) {
            return (WorkflowRun) exec;
        }
        return null;
    }

    boolean isStageNode(BlockStartNode flowNode) {
        if (flowNode == null) {
            return false;
        }
        if (flowNode.getAction(StageAction.class) != null) {
            // Legacy style stage block without a body
            // https://groups.google.com/g/jenkinsci-users/c/MIVk-44cUcA
            return true;
        }
        if (flowNode.getAction(ThreadNameAction.class) != null) {
            // TODO comment
            return false;
        }
        return flowNode.getAction(LabelAction.class) != null;
    }

    String getStageName(@Nonnull BlockStartNode flowNode) {
        ThreadNameAction threadNameAction = flowNode.getAction(ThreadNameAction.class);
        if (threadNameAction != null) {
            return threadNameAction.getThreadName();
        }
        return flowNode.getDisplayName();
    }

    long getTime(FlowNode node) {
        TimingAction time = node.getAction(TimingAction.class);
        if(time != null) {
            return time.getStartTime();
        }
        return 0;
    }

    long getTime(FlowNode startNode, FlowNode endNode) {
        TimingAction startTime = startNode.getAction(TimingAction.class);
        TimingAction endTime = endNode.getAction(TimingAction.class);

        if (startTime != null && endTime != null) {
            return endTime.getStartTime() - startTime.getStartTime();
        }
        return 0;
    }

    static @CheckForNull
    BuildSpanAction buildTraceActionFor(final FlowExecution exec) {
        BuildSpanAction buildSpanAction = null;
        Run<?, ?> run = runFor(exec);
        if (run != null) {
            buildSpanAction = run.getAction(BuildSpanAction.class);
        }
        return buildSpanAction;
    }

    static String resultForNode(FlowNode flowNode) {
        Result result = Result.SUCCESS;

        final ErrorAction errorAction = flowNode.getError();
        final WarningAction warningAction = flowNode.getAction(WarningAction.class);

        if(errorAction != null) {
            result = Result.FAILURE;
        } else if (warningAction != null) {
            result = Result.UNSTABLE;
        }

        return result.toString();
    }

    /**
     * Gets the jenkins run object of the specified executing workflow.
     *
     * @param exec execution of a workflow
     * @return jenkins run object of a job
     */
    private static @CheckForNull Run<?, ?> runFor(FlowExecution exec) {
        Queue.Executable executable;
        try {
            executable = exec.getOwner().getExecutable();
        } catch (IOException x) {
            DatadogUtilities.severe(logger, x, "");
            return null;
        }
        if (executable instanceof Run) {
            return (Run<?, ?>) executable;
        } else {
            return null;
        }
    }

    public DatadogTracePipelineLogic getTracePipelineLogic(){
        return DatadogTracePipelineLogic.get();
    }
}
