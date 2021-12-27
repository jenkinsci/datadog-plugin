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
import hudson.model.FreeStyleProject;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.Run;

import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * This class registers a {@link PeriodicWork} with Jenkins to run periodically in order to enable
 * us to compute metrics related to the Jenkins queue.
 */
@Extension
public class DatadogQueuePublisher extends PeriodicWork {

    private static final Logger logger = Logger.getLogger(DatadogQueuePublisher.class.getName());

    private static final long RECURRENCE_PERIOD = TimeUnit.MINUTES.toMillis(1);
    private final Queue queue = Queue.getInstance();
    
    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD;
    }

    @Override
    protected void doRun() throws Exception {
        try {
            logger.fine("doRun called: Computing queue metrics");

            // Get Datadog Client Instance
            DatadogClient client = ClientFactory.getClient();
            if(client == null){
                return;
            }

            Map<String, Set<String>> tags = DatadogUtilities.getTagsFromGlobalTags();
            // Add JenkinsUrl Tag
            tags = TagsUtil.addTagToTags(tags, "jenkins_url", DatadogUtilities.getJenkinsUrl());
            long size = 0;
            long buildable = 0;
            long pending = 0;
            long stuck = 0;
            long blocked = 0;
            String hostname = DatadogUtilities.getHostname(null);
            final Queue.Item[] items = queue.getItems();
            for (Queue.Item item : items) {
                Map<String, Set<String>> job_tags = DatadogUtilities.getTagsFromGlobalTags();
                job_tags = TagsUtil.addTagToTags(job_tags, "jenkins_url", DatadogUtilities.getJenkinsUrl());
                
                String job_name;
                Task task = item.task;
                if (task instanceof FreeStyleProject) {
                    job_name = task.getFullDisplayName();
                } else if (task instanceof ExecutorStepExecution.PlaceholderTask) {
                    Run<?, ?> run = ((ExecutorStepExecution.PlaceholderTask) task).runForDisplay();
                    if (run != null) {
                        job_name = run.getParent().getFullName();
                    } else {
                        job_name = "unknown";
                    }
                } else {
                    job_name = "unknown";
                }
                TagsUtil.addTagToTags(job_tags, "job_name", job_name);
                boolean isStuck = false;
                boolean isBuildable = false;
                boolean isBlocked = false;
                boolean isPending = false;
                
                size++;
                if(item.isStuck()){
                    isStuck = true;
                    stuck++;
                }
                if (item.isBuildable()){
                    isBuildable = true;
                    buildable++;
                }
                if(item.isBlocked()){
                    isBlocked = true;
                    blocked++;
                }
                if(queue.isPending(task)){
                    isPending = true;
                    pending++;
                }
                
                client.gauge("jenkins.queue.job.in_queue", 1, hostname, job_tags);
                client.gauge("jenkins.queue.job.buildable", DatadogUtilities.toInt(isBuildable), hostname, job_tags);
                client.gauge("jenkins.queue.job.pending", DatadogUtilities.toInt(isPending), hostname, job_tags);
                client.gauge("jenkins.queue.job.stuck", DatadogUtilities.toInt(isStuck), hostname, job_tags);
                client.gauge("jenkins.queue.job.blocked", DatadogUtilities.toInt(isBlocked), hostname, job_tags);
            }

            client.gauge("jenkins.queue.size", size, hostname, tags);
            client.gauge("jenkins.queue.buildable", buildable, hostname, tags);
            client.gauge("jenkins.queue.pending", pending, hostname, tags);
            client.gauge("jenkins.queue.stuck", stuck, hostname, tags);
            client.gauge("jenkins.queue.blocked", blocked, hostname, tags);

        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to compute and send queue metrics");
        }
    }
}
