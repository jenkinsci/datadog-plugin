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


import hudson.util.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.logs.LogWriteStrategy;
import org.datadog.jenkins.plugins.datadog.metrics.MetricsClient;
import org.datadog.jenkins.plugins.datadog.traces.write.Payload;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategy;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategyImpl;
import org.datadog.jenkins.plugins.datadog.traces.write.Track;
import org.datadog.jenkins.plugins.datadog.util.CircuitBreaker;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

/**
 * This class is used to collect all methods that has to do with transmitting
 * data to Datadog.
 */
public class DatadogApiClient implements DatadogClient {

    private static final int PAYLOAD_SIZE_LIMIT = 5 * 1024 * 1024; // 5 MB

    private static final Logger logger = Logger.getLogger(DatadogApiClient.class.getName());

    private static final String EVENT = "v1/events";
    private static final String METRIC = "v1/series";
    private static final String SERVICECHECK = "v1/check_run";
    private static final String VALIDATE = "v1/validate";

    /* Timeout of 1 minutes for connecting and reading.
     * this prevents this plugin from causing jobs to hang in case of
     * flaky network or Datadog being down. Left intentionally long.
     */
    private static final int HTTP_TIMEOUT_MS = 60 * 1000;
    public static final boolean COMPRESS_REQUEST = true;

    private final String url;
    private final String logIntakeUrl;
    private final String webhookIntakeUrl;
    private final Secret apiKey;

    private final HttpClient httpClient;

    public DatadogApiClient(String url, String logIntakeUrl, String webhookIntakeUrl, Secret apiKey) {
        this.url = url;
        this.apiKey = apiKey;
        this.logIntakeUrl = logIntakeUrl;
        this.webhookIntakeUrl = webhookIntakeUrl;
        this.httpClient = new HttpClient(HTTP_TIMEOUT_MS);
    }

    public boolean event(DatadogEvent event) {
        logger.fine("Sending event");
        try {
            JSONObject payload = new JSONObject();
            payload.put("title", event.getTitle());
            payload.put("text", event.getText());
            payload.put("host", event.getHost());
            payload.put("aggregation_key", event.getAggregationKey());
            payload.put("date_happened", event.getDate());
            payload.put("tags", TagsUtil.convertTagsToJSONArray(event.getTags()));
            payload.put("source_type_name", "jenkins");
            payload.put("priority", event.getPriority().name().toLowerCase());
            payload.put("alert_type", event.getAlertType().name().toLowerCase());
            postApi(payload, EVENT);
            return true;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to send event");
            return false;
        }
    }

    @Override
    public MetricsClient metrics() {
        return new ApiMetrics();
    }

    private final class ApiMetrics implements MetricsClient {
        // when we submit a rate we need to divide the submitted value by the interval (10)
        private static final int RATE_INTERVAL = 10;

        private final JSONArray series = new JSONArray();
        private final long timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

        @Override
        public void gauge(String name, double value, String hostname, Map<String, Set<String>> tags) {
            addMetric(name, value, hostname, tags, "gauge");
        }

        @Override
        public void rate(String name, double value, String hostname, Map<String, Set<String>> tags) {
            addMetric(name, value, hostname, tags, "rate");
        }

        private void addMetric(String name, double value, String hostname, Map<String, Set<String>> tags, String type) {
            logger.fine(String.format("Sending metric '%s' with value %s", name, value));

            JSONArray point = new JSONArray();
            point.add(timestamp);
            if (type.equals("rate")) {
                point.add(value / RATE_INTERVAL);
            } else {
                point.add(value);
            }

            // Setup data point, of type [<unix_timestamp>, <value>]
            JSONArray points = new JSONArray();
            points.add(point); // api expects a list of points

            JSONObject metric = new JSONObject();
            metric.put("metric", name);
            metric.put("points", points);
            metric.put("type", type);
            metric.put("host", hostname);

            JSONObject metadata = this.getOriginMetadata();
            metric.put("metadata", metadata);

            if(type.equals("rate")) {
                metric.put("interval", RATE_INTERVAL);
            }
            if (tags != null) {
                logger.fine(tags.toString());
                metric.put("tags", TagsUtil.convertTagsToJSONArray(tags));
            }

            // Place metric as item of series list
            series.add(metric);
        }

        @Override
        public void close() throws Exception {
            // Add series to payload
            JSONObject payload = new JSONObject();
            payload.put("series", series);

            logger.fine(String.format("payload: %s", payload));
            postApi(payload, METRIC);
        }

        public JSONObject getOriginMetadata() {
            JSONObject metadata = new JSONObject();
            JSONObject origin = new JSONObject();

            // Agent
            origin.put("origin_product", 10);
            // Integration
            origin.put("origin_sub_product", 11);
            // Jenkins
            origin.put("origin_product_detail", 436);

            metadata.put("origin", origin);
            return metadata;
        }
    }

    @Override
    public boolean serviceCheck(String name, Status status, String hostname, Map<String, Set<String>> tags) {
        logger.fine(String.format("Sending service check '%s' with status %s", name, status));

        // Build payload
        JSONObject payload = new JSONObject();
        payload.put("check", name);
        payload.put("host_name", hostname);
        payload.put("timestamp", System.currentTimeMillis() / 1000); // current time, s
        payload.put("status", status.toValue());

        // Remove result tag, so we don't create multiple service check groups
        if (tags != null) {
            logger.fine(tags.toString());
            payload.put("tags", TagsUtil.convertTagsToJSONArray(tags));
        }

        try {
            postApi(payload, SERVICECHECK);
            return true;
        } catch (IOException e) {
            DatadogUtilities.severe(logger, e, "Failed to send service check");
            return false;
        }
    }

    /**
     * Posts a given {@link JSONObject} payload to the Datadog API, using the
     * user configured apiKey.
     *
     * @param payload - A JSONObject containing a specific subset of a builds metadata.
     * @param type    - A String containing the URL subpath pertaining to the type of API post required.
     */
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    private void postApi(final JSONObject payload, final String type) throws IOException {
        String url = this.url + type;

        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(apiKey));

        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

        httpClient.postAsynchronously(url, headers, "application/json", body);
    }

    @Override
    public LogWriteStrategy createLogWriteStrategy() {
        if (logIntakeUrl == null || logIntakeUrl.isEmpty()) {
            logger.severe("Datadog Log Intake URL is not set properly, logs will not be written to Datadog");
            return LogWriteStrategy.NO_OP;
        }
        return new ApiLogWriteStrategy(logIntakeUrl, apiKey, httpClient);
    }

    private static final class ApiLogWriteStrategy implements LogWriteStrategy {
        private final CircuitBreaker<List<JSONObject>> circuitBreaker;

        public ApiLogWriteStrategy(String logIntakeUrl, Secret apiKey, HttpClient httpClient) {
            Map<String, String> headers = Map.of(
                    "DD-API-KEY", Secret.toString(apiKey));
            JsonPayloadSender<JSONObject> payloadSender = new BatchSender<>(
                    httpClient,
                    logIntakeUrl,
                    headers,
                    PAYLOAD_SIZE_LIMIT,
                    Function.identity(),
                    COMPRESS_REQUEST);

            this.circuitBreaker = new CircuitBreaker<>(
                    payloadSender::send,
                    this::fallback,
                    this::handleError,
                    100,
                    CircuitBreaker.DEFAULT_MAX_HEALTH_CHECK_DELAY_MILLIS,
                    CircuitBreaker.DEFAULT_DELAY_FACTOR);
        }

        @Override
        public void send(List<JSONObject> logs) {
            circuitBreaker.accept(logs);
        }

        private void handleError(Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to post logs");
        }

        private void fallback(List<JSONObject> payloads) {
            // cannot establish connection to API, do nothing
        }

        @Override
        public void close() {
            // do nothing
        }
    }

    @Override
    public TraceWriteStrategy createTraceWriteStrategy() {
        DatadogGlobalConfiguration datadogGlobalDescriptor = DatadogUtilities.getDatadogGlobalDescriptor();
        String urlParameters = datadogGlobalDescriptor != null ? "?service=" + datadogGlobalDescriptor.getCiInstanceName() : "";
        String url = webhookIntakeUrl + urlParameters;

        Map<String, String> headers = Map.of(
            "DD-API-KEY", Secret.toString(apiKey),
            "DD-CI-PROVIDER-NAME", "jenkins");
        JsonPayloadSender<Payload> payloadSender = new BatchSender<>(httpClient, url, headers, PAYLOAD_SIZE_LIMIT, Payload::getJson, COMPRESS_REQUEST);

        return new TraceWriteStrategyImpl(Track.WEBHOOK, payloadSender::send);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DatadogApiClient that = (DatadogApiClient) o;
        return Objects.equals(url, that.url)
                && Objects.equals(logIntakeUrl, that.logIntakeUrl)
                && Objects.equals(webhookIntakeUrl, that.webhookIntakeUrl)
                && Objects.equals(apiKey, that.apiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, logIntakeUrl, webhookIntakeUrl, apiKey);
    }
}
