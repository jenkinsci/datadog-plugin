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

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.metrics.Metrics;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

/**
 * This class registers an {@link SaveableListener} to trigger events and calculate metrics:
 * - When an saveable gets changed, the {@link #onChange(Saveable, XmlFile)} method will be invoked.
 */
@Extension
public class DatadogSaveableListener  extends SaveableListener {

    private static final Logger logger = Logger.getLogger(DatadogSaveableListener.class.getName());

    @Override
    public void onChange(Saveable config, XmlFile file) {
        try {
            // Get the list of global tags to apply
            Map<String, Set<String>> tags = DatadogUtilities.getTagsFromGlobalTags();
            // Add userId and JenkinsUrl Tags
            tags = TagsUtil.addTagToTags(tags, "user_id", DatadogUtilities.getUserId());
            tags = TagsUtil.addTagToTags(tags, "jenkins_url", DatadogUtilities.getJenkinsUrl());

            logger.fine("Start DatadogSaveableListener#onChange");

            // Get Datadog Client Instance
            DatadogClient client = ClientHolder.getClient();
            if(client == null){
                return;
            }

            String hostname = DatadogUtilities.getHostname(null);
            Metrics.getInstance().incrementCounter("jenkins.config.changed", hostname, tags);

            logger.fine("End DatadogSaveableListener#onChange");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process saveable change event");
        }
    }
}
