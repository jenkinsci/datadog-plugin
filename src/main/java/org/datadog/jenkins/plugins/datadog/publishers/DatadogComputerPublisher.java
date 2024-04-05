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

package org.datadog.jenkins.plugins.datadog.publishers;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import hudson.model.Run;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.metrics.MetricsClient;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

/**
 * This class registers a {@link PeriodicWork} with Jenkins to run periodically in order to enable
 * us to compute metrics related to nodes and executors.
 */
@Extension
public class DatadogComputerPublisher extends PeriodicWork {

    private static final Logger logger = Logger.getLogger(DatadogComputerPublisher.class.getName());

    private static final long RECURRENCE_PERIOD = TimeUnit.MINUTES.toMillis(1);

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD;
    }

    @Override
    protected void doRun() throws Exception {
        logger.fine("doRun called: Computing Node metrics");

        DatadogClient client = ClientHolder.getClient();
        if (client == null) {
            return;
        }

        publishMetrics(client);
    }

    public static void publishMetrics(DatadogClient client) {
        try (MetricsClient metrics = client.metrics()) {
            String hostname = DatadogUtilities.getHostname(null);
            long nodeCount = 0;
            long nodeOffline = 0;
            long nodeOnline = 0;
            Jenkins jenkins = Jenkins.getInstance();
            Computer[] computers = new Computer[0];
            if(jenkins != null){
                computers = jenkins.getComputers();
            }
            Map<String, Set<String>> globalTags = DatadogUtilities.getTagsFromGlobalTags();
            // Add JenkinsUrl Tag
            globalTags = TagsUtil.addTagToTags(globalTags, "jenkins_url", DatadogUtilities.getJenkinsUrl());
            for (Computer computer : computers) {
                Map<String, Set<String>> tags = TagsUtil.merge(
                        DatadogUtilities.getComputerTags(computer), globalTags);
                nodeCount++;
                if (computer.isOffline()) {
                    nodeOffline++;
                    metrics.gauge("jenkins.node_status.up", 0, hostname, tags);
                }
                if (computer.isOnline()) {
                    nodeOnline++;
                    metrics.gauge("jenkins.node_status.up", 1, hostname, tags);
                }
                int executorCount = computer.countExecutors();
                int inUse = computer.countBusy();
                int free = computer.countIdle();

                metrics.gauge("jenkins.node_status.count", 1, hostname, tags);

                metrics.gauge("jenkins.executor.count", executorCount, hostname, tags);
                metrics.gauge("jenkins.executor.in_use", inUse, hostname, tags);
                metrics.gauge("jenkins.executor.free", free, hostname, tags);
            }

            metrics.gauge("jenkins.node.count", nodeCount, hostname, globalTags);
            metrics.gauge("jenkins.node.offline", nodeOffline, hostname, globalTags);
            metrics.gauge("jenkins.node.online", nodeOnline, hostname, globalTags);
            metrics.gauge("jenkins.job.currently_building", getCurrentRunCount(computers), hostname, globalTags);

        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to compute and send node metrics");
        }
    }

    private static int getCurrentRunCount(Computer[] computers) {
        Set<Run<?, ?>> runs = new HashSet<>();
        for (Computer computer : computers) {
            List<Executor> executors = computer.getAllExecutors();
            for (Executor executor : executors) {
                Run<?, ?> run = getCurrentRun(executor);
                if (run != null) {
                    runs.add(run);
                }
            }
        }
        return runs.size();
    }

    private static Run<?, ?> getCurrentRun(Executor executor) {
        Queue.Executable executable = executor.getCurrentExecutable();
        while (executable != null) {
            if (executable instanceof Run) {
                return (Run<?, ?>) executable;
            }
            executable = executable.getParentExecutable();
        }
        return null;
    }

}
