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
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.PeriodicWork;
import hudson.model.Project;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.metrics.MetricsClient;
import org.datadog.jenkins.plugins.datadog.model.PluginData;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * This class registers a {@link PeriodicWork} with Jenkins to run periodically in order to enable
 * us to compute metrics related to Jenkins level metrics.
 */
@Extension
public class DatadogJenkinsPublisher extends PeriodicWork {

    private static final Logger logger = Logger.getLogger(DatadogJenkinsPublisher.class.getName());

    private static final long RECURRENCE_PERIOD = TimeUnit.MINUTES.toMillis(1);

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD;
    }

    @Override
    protected void doRun() throws Exception {
        try {
            logger.fine("doRun called: Computing Jenkins metrics");

            // Get Datadog Client Instance
            DatadogClient client = ClientFactory.getClient();
            String hostname = DatadogUtilities.getHostname(null);
            if(client == null){
                return;
            }

            Map<String, Set<String>> tags = DatadogUtilities.getTagsFromGlobalTags();
            // Add JenkinsUrl Tag
            tags = TagsUtil.addTagToTags(tags, "jenkins_url", DatadogUtilities.getJenkinsUrl());

            long projectCount = 0;
            Jenkins instance = Jenkins.getInstanceOrNull();
            if (instance == null) {
                logger.fine("Could not retrieve projects");
            } else {
                projectCount = instance.getAllItems(Project.class).size();
            }

            PluginData pluginData = collectPluginData(instance);
            try (MetricsClient metrics = client.metrics()) {
                metrics.gauge("jenkins.project.count", projectCount, hostname, tags);
                metrics.gauge("jenkins.plugin.count", pluginData.getCount(), hostname, tags);
                metrics.gauge("jenkins.plugin.active", pluginData.getActive(), hostname, tags);
                metrics.gauge("jenkins.plugin.failed", pluginData.getFailed(), hostname, tags);
                metrics.gauge("jenkins.plugin.inactivate", pluginData.getInactive(), hostname, tags);
                metrics.gauge("jenkins.plugin.withUpdate", pluginData.getUpdatable(), hostname, tags);
            }
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to compute and send Jenkins metrics");
        }
    }

    private PluginData collectPluginData(Jenkins instance) {
        PluginData.Builder pluginData = PluginData.newBuilder();

        if (instance == null) {
            logger.fine("Could not retrieve plugins");
            return pluginData.build();
        }

        PluginManager pluginManager = instance.getPluginManager();
        List<PluginWrapper> plugins = pluginManager.getPlugins();
        pluginData.withCount(plugins.size())
                .withFailed(pluginManager.getFailedPlugins().size());
        for (PluginWrapper w : plugins) {
            if (w.hasUpdate()) {
                pluginData.incrementUpdatable();
            }
            if (w.isActive()) {
                pluginData.incrementActive();
            } else {
                pluginData.incrementInactive();
            }
        }

        return pluginData.build();
    }
}
