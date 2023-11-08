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

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.cleanUpTraceActions;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.isPipeline;
import static org.datadog.jenkins.plugins.datadog.traces.TracerConstants.SPAN_ID_ENVVAR_KEY;
import static org.datadog.jenkins.plugins.datadog.traces.TracerConstants.TRACE_ID_ENVVAR_KEY;

import com.cloudbees.workflow.rest.external.RunExt;
import com.cloudbees.workflow.rest.external.StageNodeExt;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.Metrics;
import org.datadog.jenkins.plugins.datadog.events.BuildAbortedEventImpl;
import org.datadog.jenkins.plugins.datadog.events.BuildFinishedEventImpl;
import org.datadog.jenkins.plugins.datadog.events.BuildStartedEventImpl;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;

import org.datadog.jenkins.plugins.datadog.traces.BuildSpanManager;
import org.datadog.jenkins.plugins.datadog.traces.StepDataAction;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;


/**
 * This class registers an {@link RunListener} to trigger events and calculate metrics:
 * - When a build initializes, the {@link #onInitialize(Run)} method will be invoked.
 * - When a build starts, the {@link #onStarted(Run, TaskListener)} method will be invoked.
 * - When a build completes, the {@link #onCompleted(Run, TaskListener)} method will be invoked.
 * - When a build finalizes, the {@link #onFinalized(Run)} method will be invoked.
 */
@Extension
public class DatadogBuildListener extends RunListener<Run> {

    private static final Logger logger = Logger.getLogger(DatadogBuildListener.class.getName());


    /**
     * Called when a build is first initialized.
     * @param run - A Run object representing a particular execution of Job.
     */
    @Override
    public void onInitialize(Run run) {
        try {
            // Process only if job is NOT in excluded and is in included
            if (!DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onInitialize");

            // Get Datadog Client Instance
            DatadogClient client = getDatadogClient();
            if (client == null) {
                return;
            }

            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, null);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, "Failed to parse initialized build data");
                return;
            }

            final TraceSpan buildSpan = new TraceSpan("jenkins.build", TimeUnit.MILLISECONDS.toNanos(buildData.getStartTime(0L)));
            BuildSpanManager.get().put(buildData.getBuildTag(""), buildSpan);

            // The buildData object is stored in the BuildSpanAction to be updated
            // by the information that will be calculated when the pipeline listeners
            // were executed. This is needed because if the user build is based on
            // Jenkins Pipelines, there are many information that is missing when the
            // root span is created, such as Git info (this is calculated in an inner step
            // of the pipeline)
            final BuildSpanAction buildSpanAction = new BuildSpanAction(buildData, buildSpan.context());
            run.addAction(buildSpanAction);

            final StepDataAction stepDataAction = new StepDataAction();
            run.addAction(stepDataAction);

            // Traces
            client.startBuildTrace(buildData, run);
            logger.fine("End DatadogBuildListener#onInitialize");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process build initialization");
        }
    }

    /**
     * Called before the SCMCheckout is run in a Jenkins build.
     * This method is called after onInitialize callback.
     */
    @Override
    public Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws Run.RunnerAbortedException {
        try {
            logger.fine("Start DatadogBuildListener#setUpEnvironment");

            final BuildSpanAction buildSpanAction = build.getAction(BuildSpanAction.class);
            if(buildSpanAction == null || buildSpanAction.getBuildData() == null) {
                return new Environment() {
                };
            }

            final TraceSpan.TraceSpanContext traceSpanContext = buildSpanAction.getBuildSpanContext();
            final long traceId = traceSpanContext.getTraceId();
            final long spanId = traceSpanContext.getSpanId();

            final Environment newEnv = new Environment() {
                @Override
                public void buildEnvVars(Map<String, String> env) {
                    env.put(TRACE_ID_ENVVAR_KEY, Long.toString(traceId));
                    env.put(SPAN_ID_ENVVAR_KEY, Long.toString(spanId));
                }
            };

            logger.fine("End DatadogBuildListener#setUpEnvironment");
            return newEnv;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
            return new Environment() {
            };
        }
    }

    /**
     * Called when a build is first started.
     *
     * @param run - A Run object representing a particular execution of Job.
     * @param listener - A TaskListener object which receives events that happen during some
     * operation.
     */
    @Override
    public void onStarted(Run run, TaskListener listener) {
        try {
            // Process only if job is NOT in excluded and is in included
            if (!DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onStarted");

            // Get Datadog Client Instance
            DatadogClient client = getDatadogClient();
            if (client == null) {
                return;
            }

            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, listener);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, "Failed to parse started build data");
                return;
            }

            // Send an event
            if (DatadogUtilities.shouldSendEvent(BuildStartedEventImpl.BUILD_STARTED_EVENT_NAME)) {
                DatadogEvent event = new BuildStartedEventImpl(buildData);
                client.event(event);
            }
            // Send a metric
            // item.getInQueueSince() may raise a NPE if a worker node is spinning up to run the job.
            // This could be expected behavior with ec2 spot instances/ecs containers, meaning no waiting
            // queue times if the plugin is spinning up an instance/container for one/first job.
            Queue queue = getQueue();
            Queue.Item item = queue.getItem(run.getQueueId());
            Map<String, Set<String>> tags = buildData.getTags();
            String hostname = buildData.getHostname("unknown");
            try (Metrics metrics = client.metrics()) {
                long waitingMs = (DatadogUtilities.currentTimeMillis() - item.getInQueueSince());
                metrics.gauge("jenkins.job.waiting", TimeUnit.MILLISECONDS.toSeconds(waitingMs), hostname, tags);

                final BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
                if(buildSpanAction != null && buildSpanAction.getBuildData() != null) {
                    buildSpanAction.getBuildData().setMillisInQueue(waitingMs);
                }
            } catch (NullPointerException e) {
                logger.warning("Unable to compute 'waiting' metric. " +
                        "item.getInQueueSince() unavailable, possibly due to worker instance provisioning");
            }

            // Submit counter
            client.incrementCounter("jenkins.job.started", hostname, tags);

            logger.fine("End DatadogBuildListener#onStarted");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process build start");
        }
    }

    /**
     * Called when a build is completed.
     *
     * @param run - A Run object representing a particular execution of Job.
     * @param listener - A TaskListener object which receives events that happen during some
     * operation.
     */

    @Override
    public void onCompleted(Run run, @Nonnull TaskListener listener) {
        DatadogClient client;
        try {
            // Process only if job in NOT in excluded and is in included
            if (!DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
                return;
            }

            logger.fine("Start DatadogBuildListener#onCompleted");

            client = getDatadogClient();
            if (client == null) {
                return;
            }

        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process build completion");
            return;
        }

        try (Metrics metrics = client.metrics()) {
            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, listener);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, "Failed to parse completed build data");
                return;
            }

            final boolean shouldSendEvent = DatadogUtilities.shouldSendEvent(BuildFinishedEventImpl.BUILD_FINISHED_EVENT_NAME);
            if (shouldSendEvent) {
                DatadogEvent event = new BuildFinishedEventImpl(buildData);
                client.event(event);
            }

            // Send a metric
            Map<String, Set<String>> tags = buildData.getTags();
            String hostname = buildData.getHostname("unknown");
            metrics.gauge("jenkins.job.duration", buildData.getDuration(0L) / 1000, hostname, tags);
            logger.fine(String.format("[%s]: Duration: %s", buildData.getJobName(null), toTimeString(buildData.getDuration(0L))));

            if (run instanceof WorkflowRun) {
                RunExt extRun = getRunExtForRun((WorkflowRun) run);
                long pauseDuration = 0;
                for (StageNodeExt stage : extRun.getStages()) {
                    pauseDuration += stage.getPauseDurationMillis();
                }
                metrics.gauge("jenkins.job.pause_duration", pauseDuration / 1000, hostname, tags);
                logger.fine(String.format("[%s]: Pause Duration: %s", buildData.getJobName(null), toTimeString(pauseDuration)));
                long buildDuration = run.getDuration() - pauseDuration;
                metrics.gauge("jenkins.job.build_duration", buildDuration / 1000, hostname, tags);
                logger.fine(
                        String.format("[%s]: Build Duration (without pause): %s", buildData.getJobName(null), toTimeString(buildDuration)));
            }

            // Submit counter
            client.incrementCounter("jenkins.job.completed", hostname, tags);

            // Send a service check
            String buildResult = buildData.getResult(Result.NOT_BUILT.toString());
            DatadogClient.Status status = DatadogClient.Status.UNKNOWN;
            if (Result.SUCCESS.toString().equals(buildResult)) {
                status = DatadogClient.Status.OK;
            } else if (Result.UNSTABLE.toString().equals(buildResult) ||
                    Result.ABORTED.toString().equals(buildResult) ||
                    Result.NOT_BUILT.toString().equals(buildResult)) {
                status = DatadogClient.Status.WARNING;
            } else if (Result.FAILURE.toString().equals(buildResult)) {
                status = DatadogClient.Status.CRITICAL;
            }
            // Get all tags from buildData except the result tag that is used as the SC status.
            Map<String, Set<String>> serviceCheckTags = buildData.getTags();
            serviceCheckTags.remove("result");

            client.serviceCheck("jenkins.job.status", status, hostname, serviceCheckTags);

            if (run.getResult() == Result.SUCCESS) {
                long mttr = getMeanTimeToRecovery(run);
                long cycleTime = getCycleTime(run);
                long leadTime = run.getDuration() + mttr;

                metrics.gauge("jenkins.job.leadtime", leadTime / 1000, hostname, tags);
                logger.fine(String.format("[%s]: Lead time: %s", buildData.getJobName(null), toTimeString(leadTime)));
                if (cycleTime > 0) {
                    metrics.gauge("jenkins.job.cycletime", cycleTime / 1000, hostname, tags);
                    logger.fine(String.format("[%s]: Cycle Time: %s", buildData.getJobName(null), toTimeString(cycleTime)));
                }
                if (mttr > 0) {
                    metrics.gauge("jenkins.job.mttr", mttr / 1000, hostname, tags);
                    logger.fine(String.format("[%s]: MTTR: %s", buildData.getJobName(null), toTimeString(mttr)));
                }
            } else {
                long feedbackTime = run.getDuration();
                long mtbf = getMeanTimeBetweenFailure(run);

                metrics.gauge("jenkins.job.feedbacktime", feedbackTime / 1000, hostname, tags);
                logger.fine(String.format("[%s]: Feedback Time: %s", buildData.getJobName(null), toTimeString(feedbackTime)));
                if (mtbf > 0) {
                    metrics.gauge("jenkins.job.mtbf", mtbf / 1000, hostname, tags);
                    logger.fine(String.format("[%s]: MTBF: %s", buildData.getJobName(null), toTimeString(mtbf)));
                }
            }

            logger.fine("End DatadogBuildListener#onCompleted");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process build completion");
        }
    }


    /**
     * Called when a build is finalized.
     * @param run - A Run object representing a particular execution of Job.
     */
    @Override
    public void onFinalized(Run run) {
        try {
            // Process only if job in NOT in excluded and is in included
            if (!DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onFinalized");

            // Get Datadog Client Instance
            DatadogClient client = getDatadogClient();
            if (client == null) {
                return;
            }

            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, null);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, "Failed to parse finalized build data");
                return;
            }

            // APM Traces
            client.finishBuildTrace(buildData, run);
            logger.fine("End DatadogBuildListener#onFinalized");

            BuildSpanManager.get().remove(buildData.getBuildTag(""));

        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process build finalization");
        } finally {
            // If the run belongs to a Jenkins pipeline (based on FlowNodes),
            // the `onFinalized` method is executed before processing the last node.
            // This means we cannot clean up trace actions at this point if the run is a Jenkins pipeline.
            // The trace actions will be removed after last FlowNode has been processed.
            // (See DatadogTracePipelineLogic.execute(...) method)
            if(!isPipeline(run)) {
                // Explicit removal of InvisibleActions used to collect Traces when the Run finishes.
                cleanUpTraceActions(run);
            }
        }
    }

    @Override
    public void onDeleted(Run run) {
        try {
            // Process only if job is NOT in excluded and is in included
            if (!DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onDeleted");

            // Get Datadog Client Instance
            DatadogClient client = getDatadogClient();
            if (client == null) {
                return;
            }

            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, null);
            } catch (IOException | InterruptedException | NullPointerException e) {
                DatadogUtilities.severe(logger, e, "Failed to parse deleted build data");
                return;
            }

            // If the build already complete, this could be a Jenkins cleanup operation
            if (buildData.isCompleted()) {
                String result = buildData.getResult(null);
                String number = buildData.getBuildNumber("unknown");
                String jobName = buildData.getJobName("unknown");

                // Build title
                // eg: `job_name build #1 aborted on hostname`
                String text = "Ignoring deletion event for completed Job " + jobName +
                        " build #" + number + " with result " + result;

                logger.fine(text);
                return;
            }

            // Get the list of global tags to apply
            String hostname = buildData.getHostname("unknown");

            // Send an event
            final boolean shouldSendEvent = DatadogUtilities.shouldSendEvent(BuildAbortedEventImpl.BUILD_ABORTED_EVENT_NAME);
            if (shouldSendEvent) {
                DatadogEvent event = new BuildAbortedEventImpl(buildData);
                client.event(event);
            }

            // Submit counter
            Map<String, Set<String>> tags = buildData.getTags();
            client.incrementCounter("jenkins.job.aborted", hostname, tags);

            logger.fine("End DatadogBuildListener#onDeleted");
        } catch (Exception e) {
            String text = "Failed to process build deletion: " + e;
            logger.fine(text);
        }
    }

    private String toTimeString(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%d min, %d sec", minutes, seconds);
    }

    private long getMeanTimeBetweenFailure(Run<?, ?> run) {
        Run<?, ?> lastGreenRun = run.getPreviousNotFailedBuild();
        if (lastGreenRun != null) {
            return DatadogUtilities.getRunStartTimeInMillis(run) -
                    DatadogUtilities.getRunStartTimeInMillis(lastGreenRun);
        }
        return 0;
    }

    private long getCycleTime(Run<?, ?> run) {
        Run<?, ?> previousSuccessfulBuild = run.getPreviousSuccessfulBuild();
        if (previousSuccessfulBuild != null) {
            return (DatadogUtilities.getRunStartTimeInMillis(run) + run.getDuration()) -
                    (DatadogUtilities.getRunStartTimeInMillis(previousSuccessfulBuild) +
                            previousSuccessfulBuild.getDuration());
        }
        return 0;
    }

    private long getMeanTimeToRecovery(Run<?, ?> run) {
        if (isFailedBuild(run.getPreviousBuiltBuild())) {
            Run<?, ?> firstFailedRun = run.getPreviousBuiltBuild();

            while (firstFailedRun != null && isFailedBuild(firstFailedRun.getPreviousBuiltBuild())) {
                firstFailedRun = firstFailedRun.getPreviousBuiltBuild();
            }
            if (firstFailedRun != null) {
                return DatadogUtilities.getRunStartTimeInMillis(run) -
                        DatadogUtilities.getRunStartTimeInMillis(firstFailedRun);
            }
        }
        return 0;
    }


    private boolean isFailedBuild(Run<?, ?> run) {
        return run != null && run.getResult() != Result.SUCCESS;
    }

    public RunExt getRunExtForRun(WorkflowRun run) {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        if (cfg.isCacheBuildRuns()) {
            return RunExt.create(run);
        } else {
            return RunExt.createNew(run);
        }
    }

    public Queue getQueue() {
        return Queue.getInstance();
    }

    public DatadogClient getDatadogClient() {
        return ClientFactory.getClient();
    }
}
