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
import static org.datadog.jenkins.plugins.datadog.traces.TracerConstants.SPAN_ID_ENVVAR_KEY;
import static org.datadog.jenkins.plugins.datadog.traces.TracerConstants.TRACE_ID_ENVVAR_KEY;

import com.cloudbees.workflow.rest.external.RunExt;
import com.cloudbees.workflow.rest.external.StageNodeExt;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogClientConfiguration;
import org.datadog.jenkins.plugins.datadog.events.BuildAbortedEventImpl;
import org.datadog.jenkins.plugins.datadog.events.BuildFinishedEventImpl;
import org.datadog.jenkins.plugins.datadog.events.BuildStartedEventImpl;
import org.datadog.jenkins.plugins.datadog.metrics.Metrics;
import org.datadog.jenkins.plugins.datadog.metrics.MetricsClient;
import org.datadog.jenkins.plugins.datadog.model.*;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanManager;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriter;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriterFactory;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
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
            if (!DatadogUtilities.isJobTracked(run)) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onInitialize");

            run.addAction(new GitMetadataAction());
            run.addAction(new TraceInfoAction());
            run.addAction(new PipelineQueueInfoAction());

            BuildData buildData = BuildData.create(run, null);
            TraceSpan.TraceSpanContext buildSpanContext = new TraceSpan.TraceSpanContext();
            BuildSpanManager.get().put(buildData.getBuildTag(""), buildSpanContext);

            TraceSpan.TraceSpanContext upstreamBuildSpanContext = null;
            String upstreamBuildTag = buildData.getUpstreamBuildTag(null);
            if (upstreamBuildTag != null) {
                // try to find upstream build context saved earlier
                upstreamBuildSpanContext = BuildSpanManager.get().get(upstreamBuildTag);
                if (upstreamBuildSpanContext == null) {
                    logger.warning("Could not find upstream build span context for tag: " + upstreamBuildTag +
                            ". Try increasing " + BuildSpanManager.DD_JENKINS_SPAN_CONTEXT_STORAGE_MAX_SIZE_ENV + " if this happens regularly.");
                }
            }

            final BuildSpanAction buildSpanAction = new BuildSpanAction(buildSpanContext, upstreamBuildSpanContext);
            run.addAction(buildSpanAction);

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
            if(buildSpanAction == null || buildSpanAction.getBuildSpanContext() == null) {
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
            if (!DatadogUtilities.isJobTracked(run)) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onStarted");

            // Get Datadog Client Instance
            DatadogClient client = getDatadogClient();
            if (client == null) {
                return;
            }

            Long waitingMs;
            Queue queue = getQueue();
            Queue.Item item = queue.getItem(run.getQueueId());
            if (item != null){
                waitingMs = (DatadogUtilities.currentTimeMillis() - item.getInQueueSince());
                PipelineQueueInfoAction queueInfoAction = run.getAction(PipelineQueueInfoAction.class);
                if (queueInfoAction != null) {
                    // this needs to be set before BuildData is created, as BuildData will use this value
                    queueInfoAction.setQueueTimeMillis(waitingMs);
                }
            } else {
                // item may be null if a worker node is spinning up to run the job.
                // This could be expected behavior with ec2 spot instances/ecs containers, meaning no waiting
                // queue times if the plugin is spinning up an instance/container for one/first job.
                logger.warning("Unable to get queue waiting time. " +
                        "item.getInQueueSince() unavailable, possibly due to worker instance provisioning");
                waitingMs = null;
            }

            BuildData buildData = BuildData.create(run, listener);

            // Send an event
            if (DatadogUtilities.shouldSendEvent(BuildStartedEventImpl.BUILD_STARTED_EVENT_NAME)) {
                DatadogEvent event = new BuildStartedEventImpl(buildData);
                client.event(event);
            }

            Map<String, Set<String>> tags = buildData.getTags();
            String hostname = buildData.getHostname(DatadogUtilities.getHostname(null));

            if (waitingMs != null) {
                try (MetricsClient metrics = client.metrics()) {
                    metrics.gauge("jenkins.job.waiting", TimeUnit.MILLISECONDS.toSeconds(waitingMs), hostname, tags);
                }
            }

            Metrics.getInstance().incrementCounter("jenkins.job.started", hostname, tags);

            if (DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
                TraceWriter traceWriter = TraceWriterFactory.getTraceWriter();
                if (traceWriter != null) {
                    traceWriter.submitBuild(buildData, run);
                }
            }

            logger.fine("End DatadogBuildListener#onStarted");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Interrupted while trying to process build start");
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
            if (!DatadogUtilities.isJobTracked(run)) {
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

        try (MetricsClient metrics = client.metrics()) {
            BuildData buildData = BuildData.create(run, listener);

            final boolean shouldSendEvent = DatadogUtilities.shouldSendEvent(BuildFinishedEventImpl.BUILD_FINISHED_EVENT_NAME);
            if (shouldSendEvent) {
                DatadogEvent event = new BuildFinishedEventImpl(buildData);
                client.event(event);
            }

            // Send a metric
            Map<String, Set<String>> tags = buildData.getTags();
            String hostname = buildData.getHostname(DatadogUtilities.getHostname(null));
            metrics.gauge("jenkins.job.duration", TimeUnit.MILLISECONDS.toSeconds(buildData.getDuration(0L)), hostname, tags);
            logger.fine(String.format("[%s]: Duration: %s", buildData.getJobName(), toTimeString(buildData.getDuration(0L))));

            if (run instanceof WorkflowRun) {
                RunExt extRun = getRunExtForRun((WorkflowRun) run);
                if (extRun != null){
                    long pauseDurationMillis = 0;
                    for (StageNodeExt stage : extRun.getStages()) {
                        pauseDurationMillis += stage.getPauseDurationMillis();
                    }
                    metrics.gauge("jenkins.job.pause_duration", TimeUnit.MILLISECONDS.toSeconds(pauseDurationMillis), hostname, tags);
                    logger.fine(String.format("[%s]: Pause Duration: %s", buildData.getJobName(), toTimeString(pauseDurationMillis)));
                    long buildDurationMillis = run.getDuration() - pauseDurationMillis;
                    metrics.gauge("jenkins.job.build_duration", TimeUnit.MILLISECONDS.toSeconds(buildDurationMillis), hostname, tags);
                    logger.fine(
                            String.format("[%s]: Build Duration (without pause): %s", buildData.getJobName(), toTimeString(buildDurationMillis)));
                }
            }

            Metrics.getInstance().incrementCounter("jenkins.job.completed", hostname, tags);

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
                long mttrMillis = getMeanTimeToRecovery(run);
                long cycleTimeMillis = getCycleTime(run);
                long leadTimeMillis = run.getDuration() + mttrMillis;

                metrics.gauge("jenkins.job.leadtime", TimeUnit.MILLISECONDS.toSeconds(leadTimeMillis), hostname, tags);
                logger.fine(String.format("[%s]: Lead time: %s", buildData.getJobName(), toTimeString(leadTimeMillis)));
                if (cycleTimeMillis > 0) {
                    metrics.gauge("jenkins.job.cycletime", TimeUnit.MILLISECONDS.toSeconds(cycleTimeMillis), hostname, tags);
                    logger.fine(String.format("[%s]: Cycle Time: %s", buildData.getJobName(), toTimeString(cycleTimeMillis)));
                }
                if (mttrMillis > 0) {
                    metrics.gauge("jenkins.job.mttr", TimeUnit.MILLISECONDS.toSeconds(mttrMillis), hostname, tags);
                    logger.fine(String.format("[%s]: MTTR: %s", buildData.getJobName(), toTimeString(mttrMillis)));
                }
            } else {
                long feedbackTimeMillis = run.getDuration();
                long mtbfMillis = getMeanTimeBetweenFailure(run);

                metrics.gauge("jenkins.job.feedbacktime", TimeUnit.MILLISECONDS.toSeconds(feedbackTimeMillis), hostname, tags);
                logger.fine(String.format("[%s]: Feedback Time: %s", buildData.getJobName(), toTimeString(feedbackTimeMillis)));
                if (mtbfMillis > 0) {
                    metrics.gauge("jenkins.job.mtbf", TimeUnit.MILLISECONDS.toSeconds(mtbfMillis), hostname, tags);
                    logger.fine(String.format("[%s]: MTBF: %s", buildData.getJobName(), toTimeString(mtbfMillis)));
                }
            }

            DatadogGlobalConfiguration datadogConfiguration = DatadogUtilities.getDatadogGlobalDescriptor();
            if (datadogConfiguration != null
                && datadogConfiguration.getEnableCiVisibility()
                && datadogConfiguration.isShowDatadogLinks()) {
                BuildData linkBuildData = BuildData.create(run, listener);
                addDatadogLinkAction(run, linkBuildData, datadogConfiguration);
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
            if (!DatadogUtilities.isJobTracked(run)) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onFinalized");

            TraceWriter traceWriter = TraceWriterFactory.getTraceWriter();
            if (traceWriter == null) {
                return;
            }

            BuildData buildData = BuildData.create(run, null);

            traceWriter.submitBuild(buildData, run);
            logger.fine("End DatadogBuildListener#onFinalized");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Interrupted while processing build finalization");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process build finalization");
        } finally {
            // Explicit removal of InvisibleActions used to collect Traces when the Run finishes.
            cleanUpTraceActions(run);
        }
    }

    private static void addDatadogLinkAction(Run<?, ?> run, BuildData buildData, DatadogGlobalConfiguration datadogConfiguration) {
        String datadogAppHostname = datadogConfiguration.getDatadogAppHostname();
        if (StringUtils.isNotBlank(datadogAppHostname)){
            run.addAction(new DatadogLinkAction(buildData, datadogAppHostname));
        } else {
            // no explicit Datadog App host configured, trying to infer Datadog site using client config
            DatadogClientConfiguration clientConfiguration = datadogConfiguration.getDatadogClientConfiguration();
            String siteName = clientConfiguration.getSiteName();
            if (StringUtils.isNotBlank(siteName)) {
                run.addAction(new DatadogLinkAction(buildData, "app." + siteName));
            }
        }
    }

    @Override
    public void onDeleted(Run run) {
        try {
            // Process only if job is NOT in excluded and is in included
            if (!DatadogUtilities.isJobTracked(run)) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onDeleted");

            // Get Datadog Client Instance
            DatadogClient client = getDatadogClient();
            if (client == null) {
                return;
            }

            BuildData buildData = BuildData.create(run, null);

            // If the build already complete, this could be a Jenkins cleanup operation
            if (buildData.isCompleted()) {
                String result = buildData.getResult(null);
                String number = buildData.getBuildNumber("unknown");
                String jobName = buildData.getJobName();

                // Build title
                // eg: `job_name build #1 aborted on hostname`
                String text = "Ignoring deletion event for completed Job " + jobName +
                        " build #" + number + " with result " + result;

                logger.fine(text);
                return;
            }

            // Get the list of global tags to apply
            String hostname = buildData.getHostname(DatadogUtilities.getHostname(null));

            // Send an event
            final boolean shouldSendEvent = DatadogUtilities.shouldSendEvent(BuildAbortedEventImpl.BUILD_ABORTED_EVENT_NAME);
            if (shouldSendEvent) {
                DatadogEvent event = new BuildAbortedEventImpl(buildData);
                client.event(event);
            }

            Map<String, Set<String>> tags = buildData.getTags();
            Metrics.getInstance().incrementCounter("jenkins.job.aborted", hostname, tags);

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

    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
    public RunExt getRunExtForRun(WorkflowRun run) {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        try {
            if (cfg.isCacheBuildRuns()) {
                return RunExt.create(run);
            } else {
                return RunExt.createNew(run);
            }
        } catch (NullPointerException e) {
            // RunExt#create and RunExt#createNew may throw an NPE
            DatadogUtilities.severe(logger, e, "Error while getting RunExt");
            return null;
        }
    }

    public Queue getQueue() {
        return Queue.getInstance();
    }

    public DatadogClient getDatadogClient() {
        return ClientHolder.getClient();
    }
}
