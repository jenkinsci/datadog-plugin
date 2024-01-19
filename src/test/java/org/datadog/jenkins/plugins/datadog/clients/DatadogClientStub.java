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

import hudson.model.Run;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTraceBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTracePipelineLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookPipelineLogic;
import org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategy;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.junit.Assert;

public class DatadogClientStub implements DatadogClient {

    public List<DatadogMetric> metrics;
    public List<DatadogMetric> serviceChecks;
    public List<DatadogEventStub> events;
    public List<JSONObject> logLines;

    public DatadogClientStub() {
        this.metrics = new ArrayList<>();
        this.serviceChecks = new ArrayList<>();
        this.events = new ArrayList<>();
        this.logLines = new ArrayList<>();
    }

    @Override
    public boolean event(DatadogEvent event) {
        this.events.add(new DatadogEventStub(event));
        return true;
    }

    @Override
    public boolean incrementCounter(String name, String hostname, Map<String, Set<String>> tags) {
        for (DatadogMetric m : this.metrics) {
            if(m.same(new DatadogMetric(name, 0, hostname, convertTagMapToList(tags)))) {
                double value = m.getValue() + 1;
                this.metrics.remove(m);
                this.metrics.add(new DatadogMetric(name, value, hostname, convertTagMapToList(tags)));
                return true;
            }
        }
        this.metrics.add(new DatadogMetric(name, 1, hostname, convertTagMapToList(tags)));
        return true;
    }

    @Override
    public void flushCounters() {
        // noop
    }

    @Override
    public Metrics metrics() {
        return new StubMetrics();
    }

    public final class StubMetrics implements Metrics {
        @Override
        public void gauge(String name, long value, String hostname, Map<String, Set<String>> tags) {
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

    @Override
    public boolean sendLogs(String payloadLogs) {
        JSONObject payload = JSONObject.fromObject(payloadLogs);
        this.logLines.add(payload);
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
        if (timesSeen == count){
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
        if (timesSeen >= min){
            return true;
        }
        Assert.fail("metric { " + m.toString() + " found " + timesSeen + " times, not more than" + min);
        return false;
    }

    public boolean assertMetric(String name, String hostname, String[] tags) {
        // Assert that a metric with the same name and tags has already been submitted without checking the value.
        DatadogMetric m = new DatadogMetric(name, 0, hostname, Arrays.asList(tags));
        Optional<DatadogMetric> match = this.metrics.stream().filter(t -> t.same(m)).findFirst();
        if(match.isPresent()){
            this.metrics.remove(match.get());
            return true;
        }
        Assert.fail("metric { " + m.toString() + " does not exist (ignoring value). " +
                "metrics: {" + this.metrics.toString() + " }");
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

    public static List<String> convertTagMapToList(Map<String, Set<String>> tags){
        List<String> result = new ArrayList<>();
        for (String name : tags.keySet()) {
            Set<String> values = tags.get(name);
            for (String value : values){
                result.add(String.format("%s:%s", name, value));
            }
        }
        return result;

    }

    public static Map<String, Set<String>> addTagToMap(Map<String, Set<String>> tags, String name, String value){
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

        @Override
        public JSONObject serialize(BuildData buildData, Run<?, ?> run) {
            if (isWebhook) {
                JSONObject json = new DatadogWebhookBuildLogic().finishBuildTrace(buildData, run);
                webhooks.add(json);
                return json;
            } else {
                TraceSpan span = new DatadogTraceBuildLogic().createSpan(buildData, run);
                traces.add(span);
                return new JsonTraceSpanMapper().map(span);
            }
        }

        @Override
        public Collection<JSONObject> serialize(FlowNode flowNode, Run<?, ?> run) {
            if (isWebhook) {
                Collection<JSONObject> spans = new DatadogWebhookPipelineLogic().execute(flowNode, run);
                webhooks.addAll(spans);
                return spans;
            } else {
                Collection<TraceSpan> traceSpans = new DatadogTracePipelineLogic().collectTraces(flowNode, run);
                traces.addAll(traceSpans);
                JsonTraceSpanMapper mapper = new JsonTraceSpanMapper();
                return traceSpans.stream().map(mapper::map).collect(Collectors.toList());
            }
        }

        @Override
        public void send(List<JSONObject> spans) {
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
            if(span1.getStartNano() < span2.getStartNano()){
                return -1;
            } else if (span1.getStartNano() > span2.getStartNano()) {
                return 1;
            }
            return 0;
        });
        return spans;
    }
}
