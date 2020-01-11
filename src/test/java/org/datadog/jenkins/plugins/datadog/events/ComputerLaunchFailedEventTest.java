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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.HashMap;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.nullable;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DatadogUtilities.class, Computer.class})
public class ComputerLaunchFailedEventTest {

    @Before
    public void setUp() {
        PowerMockito.mockStatic(DatadogUtilities.class);
    }

    @Test
    public void testWithNothingSet() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(0L);
        when(DatadogUtilities.getHostname(isNull())).thenReturn(null);
        when(DatadogUtilities.getNodeName(nullable(Computer.class))).thenReturn(null);

        DatadogEvent event = new ComputerLaunchFailedEventImpl(null, null, null);

        Assert.assertTrue(event.getHost() == null);
        Assert.assertTrue(event.getDate() == 0);
        Assert.assertTrue(event.getAggregationKey() == null);
        Assert.assertTrue(event.getTags() == null);
        Assert.assertTrue(event.getTitle().equals("Jenkins node null failed to launch"));
        Assert.assertTrue(event.getText().contains("Jenkins node null failed to launch"));
        Assert.assertTrue(event.getAlertType().equals(DatadogEvent.AlertType.ERROR));
        Assert.assertTrue(event.getPriority().equals(DatadogEvent.Priority.NORMAL));
    }

    @Test
    public void testWithEverythingSet() throws IOException, InterruptedException {
        when(DatadogUtilities.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        when(DatadogUtilities.getHostname(isNull())).thenReturn("hostname");
        when(DatadogUtilities.getNodeName(nullable(Computer.class))).thenReturn("computer");

        DatadogEvent event = new ComputerLaunchFailedEventImpl(null, null, new HashMap<>());

        Assert.assertTrue(event.getHost().equals("hostname"));
        Assert.assertTrue(event.getDate() != 0);
        Assert.assertTrue(event.getAggregationKey().equals("computer"));
        Assert.assertTrue(event.getTags() != null);
        Assert.assertTrue(event.getTitle().equals("Jenkins node computer failed to launch"));
        Assert.assertTrue(event.getText().contains("Jenkins node computer failed to launch"));
        Assert.assertTrue(event.getAlertType().equals(DatadogEvent.AlertType.ERROR));
        Assert.assertTrue(event.getPriority().equals(DatadogEvent.Priority.NORMAL));
    }
}
