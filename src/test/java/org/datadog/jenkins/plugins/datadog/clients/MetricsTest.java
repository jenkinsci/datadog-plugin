package org.datadog.jenkins.plugins.datadog.clients;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.datadog.jenkins.plugins.datadog.metrics.MetricKey;
import org.datadog.jenkins.plugins.datadog.metrics.Metrics;
import org.junit.Assert;
import org.junit.Test;

public class MetricsTest {

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

    @Test
    public void testIncrementCountAndFlushThreadedEnv() {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable increment = () -> {
            Map<String, Set<String>> tags = new HashMap<>();
            DatadogClientStub.addTagToMap(tags, "tag1", "value");
            DatadogClientStub.addTagToMap(tags, "tag2", "value");
            Metrics.getInstance().incrementCounter("metric1", "host1", tags);
        };

        for (int i = 0; i < 10000; i++) {
            executor.submit(increment);
        }

        stop(executor);

        // Check counter is reset as expected
        Map<MetricKey, Integer> counters = Metrics.getInstance().getAndResetCounters();
        Assert.assertEquals("size = " + counters.size(), 1, counters.size());
        Assert.assertTrue("counters.values() = " + counters.values(), counters.containsValue(10000));

    }

    @Test
    public void testIncrementCountAndFlushThreadedEnvThreadCheck() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Runnable increment = () -> {
            Map<String, Set<String>> tags = new HashMap<>();
            DatadogClientStub.addTagToMap(tags, "tag1", "value");
            DatadogClientStub.addTagToMap(tags, "tag2", "value");
            Metrics.getInstance().incrementCounter("metric1", "host1", tags);
        };

        for (int i = 0; i < 10000; i++) {
            executor.submit(increment);
        }

        stop(executor);

        // We also check the result in a distinct thread
        ExecutorService single = Executors.newSingleThreadExecutor();
        Callable<Boolean> check = () -> {
            // Check counter is reset as expected
            Map<MetricKey, Integer> counters = Metrics.getInstance().getAndResetCounters();
            Assert.assertEquals("size = " + counters.size(), 1, counters.size());
            Assert.assertTrue("counters.values() = " + counters.values(), counters.containsValue(10000));
            return true;
        };

        Future<Boolean> value = single.submit(check);

        stop(single);

        Assert.assertTrue(value.get());
    }

    @Test
    public void testIncrementCountAndFlushThreadedEnvOneClient() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Runnable increment = () -> {
            Map<String, Set<String>> tags = new HashMap<>();
            DatadogClientStub.addTagToMap(tags, "tag1", "value");
            DatadogClientStub.addTagToMap(tags, "tag2", "value");
            Metrics.getInstance().incrementCounter("metric1", "host1", tags);
        };

        for (int i = 0; i < 10000; i++) {
            executor.submit(increment);
        }

        stop(executor);

        // Check counter is reset as expected
        Map<MetricKey, Integer> counters = Metrics.getInstance().getAndResetCounters();
        Assert.assertEquals("size = " + counters.size(), 1, counters.size());
        Assert.assertTrue("counters.values() = " + counters.values(), counters.containsValue(10000));
    }

    private static void stop(ExecutorService executor) {
        try {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("termination interrupted");
        } finally {
            if (!executor.isTerminated()) {
                System.err.println("killing non-finished tasks");
            }
            executor.shutdownNow();
        }
    }
}
