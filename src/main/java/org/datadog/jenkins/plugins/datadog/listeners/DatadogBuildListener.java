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

import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.api.DDTags;
import io.opentracing.util.GlobalTracer;
import io.opentracing.Tracer;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import io.opentracing.Scope;
import io.opentracing.Span;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.events.BuildAbortedEventImpl;
import org.datadog.jenkins.plugins.datadog.events.BuildFinishedEventImpl;
import org.datadog.jenkins.plugins.datadog.events.BuildStartedEventImpl;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.trace.DatadogTraceCache;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;


/**
 * This class registers an {@link RunListener} to trigger events and calculate metrics:
 * - When a build starts, the {@link #onStarted(Run, TaskListener)} method will be invoked.
 * - When a build finishes, the {@link #onCompleted(Run, TaskListener)} method will be invoked.
 */
@Extension
public class DatadogBuildListener extends RunListener<Run>  {

    private static final Logger logger = Logger.getLogger(DatadogBuildListener.class.getName());

    /**
     * Called when a build is first started.
     *
     * @param run      - A Run object representing a particular execution of Job.
     * @param listener - A TaskListener object which receives events that happen during some
     *                 operation.
     */
    @Override
    public void onStarted(Run run, TaskListener listener) {
        try {
            // Process only if job is NOT in blacklist and is in whitelist
            if (!DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
                return;
            }
            logger.fine("End DatadogBuildListener#onStarted");

            // Get Datadog Client Instance
            DatadogClient client = getDatadogClient();
            if(client == null){
                return;
            }

            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(run, listener);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, null);
                return;
            }

            // TODO in order to trace all log properly I should not rely on creating the buildData object and rather get the unique build id from env Variables
            // Start Tracing
            startTrace(buildData);

            // Send an event
            DatadogEvent event = new BuildStartedEventImpl(buildData);
            client.event(event);

            // Send an metric
            // item.getInQueueSince() may raise a NPE if a worker node is spinning up to run the job.
            // This could be expected behavior with ec2 spot instances/ecs containers, meaning no waiting
            // queue times if the plugin is spinning up an instance/container for one/first job.
            Queue queue = getQueue();
            Queue.Item item = queue.getItem(run.getQueueId());
            Map<String, Set<String>> tags = buildData.getTags();
            String hostname = buildData.getHostname("null");
            try {
                long waiting = (DatadogUtilities.currentTimeMillis() - item.getInQueueSince()) / 1000;
                client.gauge("jenkins.job.waiting", waiting, hostname, tags);
            } catch (NullPointerException e) {
                logger.warning("Unable to compute 'waiting' metric. " +
                        "item.getInQueueSince() unavailable, possibly due to worker instance provisioning");
            }

            // Submit counter
            client.incrementCounter("jenkins.job.started", hostname, tags);

            logger.fine("End DatadogBuildListener#onStarted");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
        }
    }

    /**
     * Called when a build is completed.
     *
     * @param run      - A Run object representing a particular execution of Job.
     * @param listener - A TaskListener object which receives events that happen during some
     *                 operation.
     */

    @Override
    public void onCompleted(Run run, @Nonnull TaskListener listener) {
        // Collect Build Data
        BuildData buildData = null;
        try {
            // Process only if job in NOT in blacklist and is in whitelist
            if (!DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onCompleted");

            // Get Datadog Client Instance
            DatadogClient client = getDatadogClient();
            if(client == null){
                return;
            }

            // Collect Build Data
            try {
                buildData = new BuildData(run, listener);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, null);
                return;
            }

            // Send an event
            DatadogEvent event = new BuildFinishedEventImpl(buildData);
            client.event(event);

            // Send a metric
            Map<String, Set<String>> tags = buildData.getTags();
            String hostname = buildData.getHostname("null");
            client.gauge("jenkins.job.duration", buildData.getDuration(0L) / 1000, hostname, tags);

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
            client.serviceCheck("jenkins.job.status", status, hostname, tags);

            if (run.getResult() == Result.SUCCESS) {
                long mttr = getMeanTimeToRecovery(run);
                long cycleTime = getCycleTime(run);
                long leadTime = run.getDuration() + mttr;

                client.gauge("jenkins.job.leadtime", leadTime / 1000, hostname, tags);
                if (cycleTime > 0) {
                    client.gauge("jenkins.job.cycletime", cycleTime / 1000, hostname, tags);
                }
                if (mttr > 0) {
                    client.gauge("jenkins.job.mttr", mttr / 1000, hostname, tags);
                }
            } else {
                long feedbackTime = run.getDuration();
                long mtbf = getMeanTimeBetweenFailure(run);

                client.gauge("jenkins.job.feedbacktime", feedbackTime / 1000, hostname, tags);
                if (mtbf > 0) {
                    client.gauge("jenkins.job.mtbf", mtbf / 1000, hostname, tags);
                }
            }

            logger.fine("End DatadogBuildListener#onCompleted");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
        } finally {
            endTrace(buildData);
        }
    }

    @Override
    public void onDeleted(Run run) {
        BuildData buildData = null;
        try {
            // Process only if job is NOT in blacklist and is in whitelist
            if (!DatadogUtilities.isJobTracked(run.getParent().getFullName())) {
                return;
            }
            logger.fine("Start DatadogBuildListener#onDeleted");

            // Get Datadog Client Instance
            DatadogClient client = getDatadogClient();
            if(client == null){
                return;
            }

            // Collect Build Data
            try {
                buildData = new BuildData(run, null);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, null);
                return;
            }

            // Get the list of global tags to apply
            String hostname = buildData.getHostname("null");

            // Send an event
            DatadogEvent event = new BuildAbortedEventImpl(buildData);
            client.event(event);

            // Submit counter
            Map<String, Set<String>> tags = buildData.getTags();
            client.incrementCounter("jenkins.job.aborted", hostname, tags);

            logger.fine("End DatadogBuildListener#onDeleted");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
        } finally {
            endTrace(buildData);
        }
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

    private void startTrace(BuildData buildData){
        if(buildData == null){
            return;
        }
        Tracer tracer = GlobalTracer.get();
        Span span = tracer.buildSpan("JenkinsBuild").withTag(DDTags.SERVICE_NAME, "jenkins").start();
        // TODO: Why are both traceId and spanId equal to 0?
        String traceId = CorrelationIdentifier.getTraceId();
        String spanId = CorrelationIdentifier.getSpanId();
        DatadogUtilities.severe(logger, null, "traceId: " + traceId + " | spanId: " + spanId);
        span.setTag(DDTags.SERVICE_NAME, "jenkins");
        // Add span to cache in order to retrieve it in the endTrace method.
        DatadogUtilities.severe(logger, null, "startTrace - Cache key: " + buildData.getBuildId(null));
        DatadogTraceCache.cache.put(buildData.getBuildId(null), new DatadogTraceCache.AugmentedSpan(span, traceId, spanId));
    }

    private void endTrace(BuildData buildData){
        if(buildData == null){
            return;
        }
        DatadogUtilities.severe(logger, null, "endTrace - Cache key: " + buildData.getBuildId(null));
        DatadogTraceCache.AugmentedSpan augmentedSpan = DatadogTraceCache.cache.remove(buildData.getBuildId(null));
        if(augmentedSpan != null) {
            Span span = augmentedSpan.span;
            if (span != null) {
                span.finish();
            } else {
                //TODO debug log message.
            }
        } else {
            //TODO debug log message.
        }
    }

    private boolean isFailedBuild(Run<?, ?> run) {
        return run != null && run.getResult() != Result.SUCCESS;
    }

    public Queue getQueue(){
        return Queue.getInstance();
    }

    public DatadogClient getDatadogClient(){
        return ClientFactory.getClient();
    }
}
