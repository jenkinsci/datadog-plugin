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

package org.datadog.jenkins.plugins.datadog.clients;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.metrics.MetricKey;
import org.datadog.jenkins.plugins.datadog.metrics.Metrics;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

public class DatadogClientTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void testHttpClientGetInstanceApiKey() {
        //validateConfiguration throws an error when given an invalid API key when the urls are valid
        Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> new DatadogApiClient("http", "test", "test", null));

        String expectedMessage = "Datadog API Key is not set properly";
        String actualMessage = exception.getMessage();
        Assert.assertEquals(actualMessage, expectedMessage);
    }

    @Test
    public void testHttpClientGetInstanceApiUrl() {
        // validateConfiguration throws an error when given an invalid url
        Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> new DatadogApiClient("", null, null, null));
        String expectedMessage = "Datadog Target URL is not set properly";
        String actualMessage = exception.getMessage();
        Assert.assertEquals(actualMessage, expectedMessage);
    }


    @Test
    public void testHttpClientGetInstanceEnableValidations() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new DatadogApiClient("https", null, null, null));
    }

    @Test
    public void testDogstatsDClientGetInstanceTargetPort() {
        // validateConfiguration throws an error when given an invalid port
        Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> new DatadogAgentClient("test", null, null, null));

        String expectedMessage = "Datadog Target Port is not set properly";
        String actualMessage = exception.getMessage();
        Assert.assertEquals(actualMessage, expectedMessage);
    }

    @Test
    public void testDogstatsDClientGetInstanceEnableValidations() {
        // calling getInstance with invalid data returns null
        Assert.assertThrows(IllegalArgumentException.class, () -> new DatadogAgentClient("https", null, null, null));
    }

    @Test
    public void testEvpProxyEnabled() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);
        DatadogAgentClient client = Mockito.spy(new DatadogAgentClient("test", 1234, 1235, 1236, 1_000));
        Mockito.doReturn(new HashSet<>(Collections.singletonList("/evp_proxy/v3/"))).when(client).fetchAgentSupportedEndpoints();
        Assert.assertTrue(client.isEvpProxySupported());
    }

    @Test
    public void testEvpProxyDisabled() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);
        DatadogAgentClient client = Mockito.spy(new DatadogAgentClient("test", 1234, 1235, 1236, 1_000));
        Mockito.doReturn(new HashSet<String>()).when(client).fetchAgentSupportedEndpoints();
        Assert.assertFalse(client.isEvpProxySupported());
    }

    @Test
    public void testEmptyAgentSupportedEndpointsWithNoAgent() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);
        DatadogAgentClient client = new DatadogAgentClient("test", 1234, 1235, 1236, 1_000);
        Assert.assertTrue(client.fetchAgentSupportedEndpoints().isEmpty());
    }

    @Test
    public void testIncrementCountAndFlush() {
        Map<String, Set<String>> tags1 = new HashMap<>();
        DatadogClientStub.addTagToMap(tags1, "tag1", "value");
        DatadogClientStub.addTagToMap(tags1, "tag2", "value");
        Metrics.getInstance().incrementCounter("metric1", "host1", tags1);
        Metrics.getInstance().incrementCounter("metric1", "host1", tags1);

        Map<String, Set<String>> tags2 = new HashMap<>();
        DatadogClientStub.addTagToMap(tags2, "tag1", "value");
        DatadogClientStub.addTagToMap(tags2, "tag2", "value");
        DatadogClientStub.addTagToMap(tags2, "tag3", "value");
        Metrics.getInstance().incrementCounter("metric1", "host1", tags2);

        Metrics.getInstance().incrementCounter("metric1", "host2", tags2);
        Metrics.getInstance().incrementCounter("metric1", "host2", tags2);

        Metrics.getInstance().incrementCounter("metric2", "host2", tags2);

        // The following code should be the same as in the flushCounters method
        Map<MetricKey, Integer> counters = Metrics.getInstance().getAndResetCounters();

        // Check counter is reset as expected
        Map<MetricKey, Integer> countersEmpty = Metrics.getInstance().getAndResetCounters();
        Assert.assertEquals("size = " + countersEmpty.size(), 0, countersEmpty.size());

        // Check that metrics to submit are correct
        boolean check1  = false, check2 = false, check3 = false, check4 = false;
        Assert.assertEquals("counters = " + counters.size(), 4, counters.size());
        for (MetricKey counterMetric: counters.keySet()) {
            int count = counters.get(counterMetric);
            if (counterMetric.getMetricName().equals("metric1") && counterMetric.getHostname().equals("host1")
                    && counterMetric.getTags().size() == 2) {
                Assert.assertEquals("count = " + count, 2, count);
                check1 = true;
            } else if (counterMetric.getMetricName().equals("metric1") && counterMetric.getHostname().equals("host1")
                    && counterMetric.getTags().size() == 3) {
                Assert.assertEquals("count = " + count, 1, count);
                check2 = true;
            } else if (counterMetric.getMetricName().equals("metric1") && counterMetric.getHostname().equals("host2")
                    && counterMetric.getTags().size() == 3) {
                Assert.assertEquals("count = " + count, 2, count);
                check3 = true;
            } else if (counterMetric.getMetricName().equals("metric2") && counterMetric.getHostname().equals("host2")
                    && counterMetric.getTags().size() == 3) {
                Assert.assertEquals("count = " + count, 1, count);
                check4 = true;
            }
        }
        Assert.assertTrue(check1 + " " + check2 + " " + check3 + " " + check4,
                check1 && check2 && check3 && check4);
    }
}
