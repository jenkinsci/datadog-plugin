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
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.metrics.MetricKey;
import org.datadog.jenkins.plugins.datadog.metrics.Metrics;
import org.datadog.jenkins.plugins.datadog.metrics.MetricsClient;

@Extension
public class DatadogCountersPublisher extends AsyncPeriodicWork {

    private static final Logger logger = Logger.getLogger(DatadogCountersPublisher.class.getName());

    public DatadogCountersPublisher() {
        super("Datadog Counters Publisher");
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(10);
    }

    @Override
    protected void execute(TaskListener taskListener) throws IOException, InterruptedException {
        logger.fine("Execute called: Publishing counters");
        DatadogClient client = ClientFactory.getClient();
        if (client == null) {
            return;
        }

        publishMetrics(client);
    }

    // exposed to trigger metrics flush in tests
    public static void publishMetrics(DatadogClient client) {
        try (MetricsClient metricsClient = client.metrics()) {
            Map<MetricKey, Integer> counters = Metrics.getInstance().getAndResetCounters();
            for (Map.Entry<MetricKey, Integer> entry : counters.entrySet()) {
                MetricKey metric = entry.getKey();
                Number value = entry.getValue();
                logger.fine("Flushing: " + metric.getMetricName() + " - " + value);
                metricsClient.rate(metric.getMetricName(), value.doubleValue(), metric.getHostname(), metric.getTags());
            }

        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to publish counters");
        }
    }

    @Override
    protected Level getNormalLoggingLevel() {
        return Level.FINE;
    }
}
