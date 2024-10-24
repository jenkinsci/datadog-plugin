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
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.security.SecurityListener;
import org.acegisecurity.userdetails.UserDetails;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.events.UserAuthenticationEventImpl;
import org.datadog.jenkins.plugins.datadog.metrics.Metrics;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

/**
 * This class registers an {@link SecurityListener} to trigger events and calculate metrics:
 * - When an user authenticates, the {@link #authenticated(UserDetails)} method will be invoked.
 * - When an user fails to authenticate, the {@link #failedToAuthenticate(String)} method will be invoked.
 * - When an user logout, the {@link #loggedOut(String)} method will be invoked.
 */
@Extension
public class DatadogSecurityListener extends SecurityListener {

    private static final Logger logger = Logger.getLogger(DatadogSecurityListener.class.getName());

    @Override
    protected void authenticated(@Nonnull UserDetails details) {
        try {

            // Get the list of global tags to apply
            Map<String, Set<String>> tags = DatadogUtilities.getTagsFromGlobalTags();
            // Add userId and JenkinsUrl Tags
            tags = TagsUtil.addTagToTags(tags, "user_id", DatadogUtilities.getUserId());
            tags = TagsUtil.addTagToTags(tags, "jenkins_url", DatadogUtilities.getJenkinsUrl());

            logger.fine("Start DatadogSecurityListener#authenticated");

            // Get Datadog Client Instance
            DatadogClient client = ClientHolder.getClient();
            if(client == null){
                return;
            }
            final boolean shouldSendEvent = DatadogUtilities.shouldSendEvent(UserAuthenticationEventImpl.USER_LOGIN_EVENT_NAME);
            if (shouldSendEvent) {
                DatadogEvent event = new UserAuthenticationEventImpl(details.getUsername(),
                    UserAuthenticationEventImpl.USER_LOGIN_MESSAGE, tags);
                client.event(event);
            }

            String hostname = DatadogUtilities.getHostname(null);
            Metrics.getInstance().incrementCounter("jenkins.user.authenticated", hostname, tags);

            logger.fine("End DatadogSecurityListener#authenticated");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process user authenticated event");
        }
    }

    @Override
    protected void failedToAuthenticate(@Nonnull String username) {
        try {
            // Get the list of global tags to apply
            Map<String, Set<String>> tags = DatadogUtilities.getTagsFromGlobalTags();
            // Add userId and JenkinsUrl Tags
            tags = TagsUtil.addTagToTags(tags, "user_id", DatadogUtilities.getUserId());
            tags = TagsUtil.addTagToTags(tags, "jenkins_url", DatadogUtilities.getJenkinsUrl());

            logger.fine("Start DatadogSecurityListener#failedToAuthenticate");

            // Get Datadog Client Instance
            DatadogClient client = ClientHolder.getClient();
            if(client == null){
                return;
            }

            final boolean shouldSendEvent = DatadogUtilities.shouldSendEvent(UserAuthenticationEventImpl.USER_ACCESS_DENIED_EVENT_NAME);
            if (shouldSendEvent) {
                DatadogEvent event = new UserAuthenticationEventImpl(username, UserAuthenticationEventImpl.USER_ACCESS_DENIED_MESSAGE, tags);
                client.event(event);
            }

            String hostname = DatadogUtilities.getHostname(null);
            Metrics.getInstance().incrementCounter("jenkins.user.access_denied", hostname, tags);

            logger.fine("End DatadogSecurityListener#failedToAuthenticate");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process authentication failure event");
        }
    }

    @Override
    protected void loggedIn(@Nonnull String username) {
        //Covered by Authenticated
    }

    @Override
    protected void failedToLogIn(@Nonnull String username) {
        //Covered by failedToAuthenticate
    }

    @Override
    protected void loggedOut(@Nonnull String username) {
        try {

            // Get the list of global tags to apply
            Map<String, Set<String>> tags = DatadogUtilities.getTagsFromGlobalTags();
            // Add userId and JenkinsUrl Tags
            tags = TagsUtil.addTagToTags(tags, "user_id", DatadogUtilities.getUserId());
            tags = TagsUtil.addTagToTags(tags, "jenkins_url", DatadogUtilities.getJenkinsUrl());

            logger.fine("Start DatadogSecurityListener#loggedOut");

            // Get Datadog Client Instance
            DatadogClient client = ClientHolder.getClient();
            if(client == null){
                return;
            }
            final boolean shouldSendEvent = DatadogUtilities.shouldSendEvent(UserAuthenticationEventImpl.USER_LOGOUT_EVENT_NAME);
            if (shouldSendEvent) {
                DatadogEvent event = new UserAuthenticationEventImpl(username, UserAuthenticationEventImpl.USER_LOGOUT_MESSAGE, tags);
                client.event(event);
            }

            String hostname = DatadogUtilities.getHostname(null);
            Metrics.getInstance().incrementCounter("jenkins.user.logout", hostname, tags);

            logger.fine("End DatadogSecurityListener#loggedOut");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process logout event");
        }
    }
}
