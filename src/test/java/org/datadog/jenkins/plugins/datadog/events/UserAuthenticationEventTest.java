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

package org.datadog.jenkins.plugins.datadog.events;

import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class UserAuthenticationEventTest {

    @Test
    public void testWithNothingSet() {
        DatadogEvent event = new UserAuthenticationEventImpl(null, null, null);

        Assert.assertEquals(event.getHost(), DatadogUtilities.getHostname(null));
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertEquals("anonymous", event.getAggregationKey());
        Assert.assertEquals(1, event.getTags().size());
        Assert.assertTrue(event.getTags().get("event_type").contains("security"));
        Assert.assertEquals("User anonymous did something", event.getTitle());
        Assert.assertTrue(event.getText().contains("User anonymous did something"));
        Assert.assertEquals(DatadogEvent.AlertType.ERROR, event.getAlertType());
        Assert.assertEquals(DatadogEvent.Priority.NORMAL, event.getPriority());
        Assert.assertEquals("unknown", event.getJenkinsUrl());

        event = new UserAuthenticationEventImpl(null, "something", null);

        Assert.assertEquals(event.getHost(), DatadogUtilities.getHostname(null));
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertEquals("anonymous", event.getAggregationKey());
        Assert.assertEquals(1, event.getTags().size());
        Assert.assertTrue(event.getTags().get("event_type").contains("security"));
        Assert.assertEquals("User anonymous something", event.getTitle());
        Assert.assertTrue(event.getText().contains("User anonymous something"));
        Assert.assertEquals(DatadogEvent.AlertType.ERROR, event.getAlertType());
        Assert.assertEquals(DatadogEvent.Priority.NORMAL, event.getPriority());
        Assert.assertEquals("unknown", event.getJenkinsUrl());
    }

    @Test
    public void testWithEverythingSet() {
        DatadogEvent event = new UserAuthenticationEventImpl("username", UserAuthenticationEventImpl.USER_ACCESS_DENIED_MESSAGE, new HashMap<>());

        String hostname = DatadogUtilities.getHostname(null);
        Assert.assertEquals(event.getHost(), hostname);
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertEquals("username", event.getAggregationKey());
        Assert.assertEquals(1, event.getTags().size());
        Assert.assertTrue(event.getTags().get("event_type").contains("security"));
        Assert.assertEquals("User username failed to authenticate", event.getTitle());
        Assert.assertTrue(event.getText(), event.getText().contains("User username failed to authenticate"));
        Assert.assertTrue(event.getText(), event.getText().contains("Host: " + hostname + ", Jenkins URL: unknown"));
        Assert.assertEquals(DatadogEvent.AlertType.ERROR, event.getAlertType());
        Assert.assertEquals(DatadogEvent.Priority.NORMAL, event.getPriority());
        Assert.assertEquals("unknown", event.getJenkinsUrl());

        event = new UserAuthenticationEventImpl("username", UserAuthenticationEventImpl.USER_LOGOUT_MESSAGE, new HashMap<>());

        Assert.assertEquals(event.getHost(), hostname);
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertEquals("username", event.getAggregationKey());
        Assert.assertEquals(1, event.getTags().size());
        Assert.assertTrue(event.getTags().get("event_type").contains("security"));
        Assert.assertEquals("User username logout", event.getTitle());
        Assert.assertTrue(event.getText(), event.getText().contains("User username logout"));
        Assert.assertTrue(event.getText(), event.getText().contains("Host: " + hostname + ", Jenkins URL: unknown"));
        Assert.assertEquals(DatadogEvent.AlertType.SUCCESS, event.getAlertType());
        Assert.assertEquals(DatadogEvent.Priority.LOW, event.getPriority());
        Assert.assertEquals("unknown", event.getJenkinsUrl());

        event = new UserAuthenticationEventImpl("username", UserAuthenticationEventImpl.USER_LOGIN_MESSAGE, new HashMap<>());

        Assert.assertEquals(event.getHost(), hostname);
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertEquals("username", event.getAggregationKey());
        Assert.assertEquals(1, event.getTags().size());
        Assert.assertTrue(event.getTags().get("event_type").contains("security"));
        Assert.assertEquals("User username authenticated", event.getTitle());
        Assert.assertTrue(event.getText(), event.getText().contains("User username authenticated"));
        Assert.assertTrue(event.getText(), event.getText().contains("Host: " + hostname + ", Jenkins URL: unknown"));
        Assert.assertEquals(DatadogEvent.AlertType.SUCCESS, event.getAlertType());
        Assert.assertEquals(DatadogEvent.Priority.LOW, event.getPriority());
        Assert.assertEquals("unknown", event.getJenkinsUrl());

    }
}
