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

import hudson.model.Computer;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ComputerOnlineEventTest {

    @Test
    public void testWithNothingSet() {
        DatadogEvent event = new ComputerOnlineEventImpl(null, null, null, false);

        String hostname = DatadogUtilities.getHostname(null);
        Assert.assertEquals(event.getHost(), hostname);
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertNull(event.getAggregationKey());
        Assert.assertEquals(1, event.getTags().size());
        Assert.assertTrue(event.getTags().get("event_type").contains("system"));
        Assert.assertEquals("Jenkins node null is online", event.getTitle());
        Assert.assertTrue(event.getText(), event.getText().contains("Jenkins node null is online"));
        Assert.assertTrue(event.getText(), event.getText().contains("Host: " + hostname + ", Jenkins URL: unknown"));
        Assert.assertEquals(DatadogEvent.AlertType.SUCCESS, event.getAlertType());
        Assert.assertEquals(DatadogEvent.Priority.LOW, event.getPriority());
        Assert.assertEquals("unknown", event.getJenkinsUrl());

        event = new ComputerOnlineEventImpl(null, null, null, true);

        Assert.assertEquals(event.getHost(), hostname);
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertNull(event.getAggregationKey());
        Assert.assertEquals(1, event.getTags().size());
        Assert.assertTrue(event.getTags().get("event_type").contains("system"));
        Assert.assertEquals("Jenkins node null is temporarily online", event.getTitle());
        Assert.assertTrue(event.getText(), event.getText().contains("Jenkins node null is temporarily online"));
        Assert.assertTrue(event.getText(), event.getText().contains("Host: " + hostname + ", Jenkins URL: unknown"));
        Assert.assertEquals(DatadogEvent.AlertType.SUCCESS, event.getAlertType());
        Assert.assertEquals(DatadogEvent.Priority.LOW, event.getPriority());
        Assert.assertEquals("unknown", event.getJenkinsUrl());
    }

    @Test
    public void testWithEverythingSet() {
        Computer computer = mock(Computer.class);
        when(computer.getName()).thenReturn("computer");

        DatadogEvent event = new ComputerOnlineEventImpl(computer, null,
                new HashMap<>(), false);

        String hostname = DatadogUtilities.getHostname(null);
        Assert.assertEquals(event.getHost(), hostname);
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertEquals("computer", event.getAggregationKey());
        Assert.assertEquals(1, event.getTags().size());
        Assert.assertTrue(event.getTags().get("event_type").contains("system"));
        Assert.assertEquals("Jenkins node computer is online", event.getTitle());
        Assert.assertTrue(event.getText(), event.getText().contains("Jenkins node computer is online"));
        Assert.assertTrue(event.getText(), event.getText().contains("Host: " + hostname + ", Jenkins URL: unknown"));
        Assert.assertEquals(DatadogEvent.AlertType.SUCCESS, event.getAlertType());
        Assert.assertEquals(DatadogEvent.Priority.LOW, event.getPriority());
        Assert.assertEquals("unknown", event.getJenkinsUrl());

        event = new ComputerOnlineEventImpl(computer, null,
                new HashMap<>(), true);

        Assert.assertEquals(event.getHost(), hostname);
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertEquals("computer", event.getAggregationKey());
        Assert.assertEquals(1, event.getTags().size());
        Assert.assertTrue(event.getTags().get("event_type").contains("system"));
        Assert.assertEquals("Jenkins node computer is temporarily online", event.getTitle());
        Assert.assertTrue(event.getText(), event.getText().contains("Jenkins node computer is temporarily online"));
        Assert.assertTrue(event.getText(), event.getText().contains("Host: " + hostname + ", Jenkins URL: unknown"));
        Assert.assertEquals(DatadogEvent.AlertType.SUCCESS, event.getAlertType());
        Assert.assertEquals(DatadogEvent.Priority.LOW, event.getPriority());
        Assert.assertEquals("unknown", event.getJenkinsUrl());
    }
}
