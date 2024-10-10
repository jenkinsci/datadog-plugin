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

import com.google.common.base.Objects;
import hudson.model.Run;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.logs.LogWriteStrategy;
import org.datadog.jenkins.plugins.datadog.metrics.MetricsClient;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTraceBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTracePipelineLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookPipelineLogic;
import org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.traces.write.Payload;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategy;
import org.datadog.jenkins.plugins.datadog.traces.write.Track;
import org.junit.Assert;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DatadogClientStub implements DatadogClient {

    public List<DatadogMetric> metrics;
    public List<DatadogMetric> serviceChecks;
    public List<DatadogEventStub> events;

    public DatadogClientStub() {
        this.metrics = new CopyOnWriteArrayList<>();
        this.serviceChecks = new CopyOnWriteArrayList<>();
        this.events = new CopyOnWriteArrayList<>();
    }

    @Override
    public boolean event(DatadogEvent event) {
        this.events.add(new DatadogEventStub(event));
        return true;
    }

    @Override
    public MetricsClient metrics() {
        return new StubMetrics();
    }

    public final class StubMetrics implements MetricsClient {
        @Override
        public void gauge(String name, double value, String hostname, Map<String, Set<String>> tags) {
            metrics.add(new DatadogMetric(name, value, hostname, convertTagMapToList(tags)));
        }

        @Override
        public void rate(String name, double value, String hostname, Map<String, Set<String>> tags) {
            metrics.add(new DatadogMetric(name, value, hostname, convertTagMapToList(tags)));
        }

        @Override
        public void close() throws Exception {
            // no op
        }
    }

    @Override
    public boolean serviceCheck(String name, Status status, String hostname, Map<String, Set<String>> tags) {
        this.serviceChecks.add(new DatadogMetric(name, status.toValue(), hostname, convertTagMapToList(tags)));
        return true;
    }

    public boolean assertMetric(String name, double value, String hostname, String[] tags) {
        DatadogMetric m = new DatadogMetric(name, value, hostname, Arrays.asList(tags));
        if (this.metrics.contains(m)) {
            this.metrics.remove(m);
            return true;
        }
        Assert.fail("metric { " + m.toString() + " does not exist. " +
                "metrics: {" + this.metrics.toString() + " }");
        return false;
    }

    /*
     * Returns the value of the asserted metric if it exists.
     */
    public double assertMetricGetValue(String name, String hostname, String[] tags) {
        DatadogMetric m = new DatadogMetric(name, 0, hostname, Arrays.asList(tags));
        Optional<DatadogMetric> match = this.metrics.stream().filter(t -> t.same(m)).findFirst();
        double value = 0;
        if (match.isPresent()) {
            value = match.get().getValue();
            this.metrics.remove(match.get());
            return value;
        }
        Assert.fail("metric { " + m.toString() + " does not exist (ignoring value). " +
                "metrics: {" + this.metrics.toString() + " }");
        return value;
    }

    /*
     * Asserts that the metric of a given value is submitted a given number of times.
     */
    public boolean assertMetricValues(String name, double value, String hostname, int count) {
        DatadogMetric m = new DatadogMetric(name, value, hostname, new ArrayList<>());

        // compare without tags so metrics of the same value are considered the same.
        long timesSeen = this.metrics.stream().filter(x -> x.sameNoTags(m)).count();
        if (timesSeen == count) {
            return true;
        }
        Assert.fail("metric { " + m.toString() + " found " + timesSeen + " times, not " + count);
        return false;
    }

    /*
     * Asserts that the metric of a given value is submitted at least a given number of times.
     */
    public boolean assertMetricValuesMin(String name, double value, String hostname, int min) {
        DatadogMetric m = new DatadogMetric(name, value, hostname, new ArrayList<>());

        // compare without tags so metrics of the same value are considered the same.
        long timesSeen = this.metrics.stream().filter(x -> x.sameNoTags(m)).count();
        if (timesSeen >= min) {
            return true;
        }
        Assert.fail("metric { " + m.toString() + " found " + timesSeen + " times, not more than" + min);
        return false;
    }

    public List<DatadogMetric> getMetrics() {
        return Collections.unmodifiableList(this.metrics);
    }

    public boolean assertMetric(String name, String hostname, String[] tags) {
        // Assert that a metric with the same name and tags has already been submitted without checking the value.
        DatadogMetric m = new DatadogMetric(name, 0, hostname, Arrays.asList(tags));
        Optional<DatadogMetric> match = this.metrics.stream().filter(t -> t.same(m)).findFirst();
        if (match.isPresent()) {
            this.metrics.remove(match.get());
            return true;
        }

        List<DatadogMetric> sameMetricsNoTags = metrics.stream().filter(t -> t.sameNoTags(m)).collect(Collectors.toList());
        if (!sameMetricsNoTags.isEmpty()) {
            Assert.fail("metric { " + m + " does not exist (ignoring value).\n" +
                    "Same metrics ignoring tags: {" + sameMetricsNoTags + " }");
        }

        List<DatadogMetric> metricsWithSameName = metrics.stream().filter(t -> Objects.equal(t.getName(), m.getName())).collect(Collectors.toList());
        if (!metricsWithSameName.isEmpty()) {
            Assert.fail("metric { " + m + " does not exist (ignoring value).\n" +
                    "Metrics with same name: {" + metricsWithSameName + " }");
        }

        Assert.fail("metric { " + m + " does not exist (ignoring value).\n" +
                "Metrics: {" + this.metrics.toString() + " }");
        return false;
    }

    public boolean assertServiceCheck(String name, int code, String hostname, String[] tags) {
        DatadogMetric m = new DatadogMetric(name, code, hostname, Arrays.asList(tags));
        if (this.serviceChecks.contains(m)) {
            this.serviceChecks.remove(m);
            return true;
        }
        Assert.fail("serviceCheck { " + m.toString() + " does not exist. " +
                "serviceChecks: {" + this.serviceChecks.toString() + "}");
        return false;
    }

    public boolean assertedAllMetricsAndServiceChecks() {
        if (this.metrics.size() == 0 && this.serviceChecks.size() == 0) {
            return true;
        }

        Assert.fail("metrics: {" + this.metrics.toString() + " }, serviceChecks : {" +
                this.serviceChecks.toString() + "}");
        return false;
    }

    public boolean assertEvent(String title, DatadogEvent.Priority priority, DatadogEvent.AlertType alertType, Long date) {
        for (DatadogEventStub event : this.events) {
            if (event.same(title, priority, alertType, date)) {
                this.events.remove(event);
                return true;
            }
        }
        Assert.fail("event matching: { " +
                "title='" + title + '\'' +
                ", priority=" + priority +
                ", alertType=" + alertType +
                ", date=" + date + "} not found. " +
                "events: {" + this.events.toString() + "}");
        return false;
    }

    public boolean assertedAllEvents() {
        if (this.events.size() == 0) {
            return true;
        }

        Assert.fail("Not all events asserted: {" + this.metrics.toString() + " }");
        return false;
    }

    public static List<String> convertTagMapToList(Map<String, Set<String>> tags) {
        List<String> result = new ArrayList<>();
        for (String name : tags.keySet()) {
            Set<String> values = tags.get(name);
            for (String value : values) {
                result.add(String.format("%s:%s", name, value));
            }
        }
        return result;

    }

    public static Map<String, Set<String>> addTagToMap(Map<String, Set<String>> tags, String name, String value) {
        Set<String> v = tags.containsKey(name) ? tags.get(name) : new HashSet<String>();
        v.add(value);
        tags.put(name, v);
        return tags;
    }

    private final StubTraceWriteStrategy traceWriteStrategy = new StubTraceWriteStrategy();

    private static final class StubTraceWriteStrategy implements TraceWriteStrategy {
        private volatile boolean isWebhook = false;
        private final Collection<TraceSpan> traces = new LinkedBlockingQueue<>();
        private final Collection<JSONObject> webhooks = new LinkedBlockingQueue<>();

        @Nullable
        @Override
        public Payload serialize(BuildData buildData, Run<?, ?> run) {
            if (buildData.isBuilding()) {
                // ignore in-progress pipeline events for now
                return null;
            }

            if (isWebhook) {
                JSONObject json = new DatadogWebhookBuildLogic().toJson(buildData, run);
                if (json == null) {
                    return null;
                }
                webhooks.add(json);
                return new Payload(json, Track.WEBHOOK);
            } else {
                TraceSpan span = new DatadogTraceBuildLogic().toSpan(buildData, run);
                if (span == null) {
                    return null;
                }
                traces.add(span);
                JSONObject json = new JsonTraceSpanMapper().map(span);
                return new Payload(json, Track.APM);
            }
        }

        @Nullable
        @Override
        public Payload serialize(PipelineStepData stepData, Run<?, ?> run) throws IOException, InterruptedException {
            if (isWebhook) {
                JSONObject json = new DatadogWebhookPipelineLogic().toJson(stepData, run);
                if (json == null) {
                    return null;
                }
                webhooks.add(json);
                return new Payload(json, Track.WEBHOOK);
            } else {
                TraceSpan span = new DatadogTracePipelineLogic().toSpan(stepData, run);
                if (span == null) {
                    return null;
                }
                traces.add(span);
                JSONObject json = new JsonTraceSpanMapper().map(span);
                return new Payload(json, Track.APM);
            }
        }

        @Override
        public void send(Collection<Payload> spans) {
            // no op
        }

        public void configureForWebhooks() {
            isWebhook = true;
        }
    }

    public void configureForWebhooks() {
        traceWriteStrategy.configureForWebhooks();
    }

    @Override
    public TraceWriteStrategy createTraceWriteStrategy() {
        return traceWriteStrategy;
    }

    @Override
    public LogWriteStrategy createLogWriteStrategy() {
        return logWriteStrategy;
    }

    private final StubLogWriteStrategy logWriteStrategy = new StubLogWriteStrategy();

    private static final class StubLogWriteStrategy implements LogWriteStrategy {
        public final List<JSONObject> logLines = new CopyOnWriteArrayList<>();

        @Override
        public void send(List<String> logs) {
            for (String log : logs) {
                JSONObject payload = JSONObject.fromObject(log);
                this.logLines.add(payload);
            }
        }

        @Override
        public void close() {
            // do nothing
        }
    }

    public List<JSONObject> getLogLines() {
        return logWriteStrategy.logLines;
    }

    public boolean waitForWebhooks(final int number) throws InterruptedException {
        long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (System.currentTimeMillis() < timeout) {
            if (traceWriteStrategy.webhooks.size() >= number) {
                return true;
            }
            Thread.sleep(100L);
        }
        if (traceWriteStrategy.webhooks.size() < number) {
            throw new AssertionError("Failed while waiting for " + number + " webhooks, got  " + traceWriteStrategy.webhooks.size() + ": " + traceWriteStrategy.webhooks);
        } else {
            return true;
        }
    }

    public boolean waitForTraces(int number) throws InterruptedException {
        long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (System.currentTimeMillis() < timeout) {
            if (traceWriteStrategy.traces.size() >= number) {
                return true;
            }
            Thread.sleep(100L);
        }
        if (traceWriteStrategy.traces.size() < number) {
            throw new AssertionError("Failed while waiting for " + number + " traces, got  " + traceWriteStrategy.traces.size() + ": " + traceWriteStrategy.traces);
        } else {
            return true;
        }
    }

    public List<JSONObject> getWebhooks() {
        return new ArrayList<>(traceWriteStrategy.webhooks);
    }

    public List<TraceSpan> getSpans() {
        ArrayList<TraceSpan> spans = new ArrayList<>(traceWriteStrategy.traces);
        Collections.sort(spans, (span1, span2) -> {
            if (span1.getStartNano() < span2.getStartNano()) {
                return -1;
            } else if (span1.getStartNano() > span2.getStartNano()) {
                return 1;
            }
            return 0;
        });
        return spans;
    }
}
