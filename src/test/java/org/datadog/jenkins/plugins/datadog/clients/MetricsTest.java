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
