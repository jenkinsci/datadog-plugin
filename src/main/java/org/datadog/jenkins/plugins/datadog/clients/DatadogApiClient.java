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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.traces.write.Span;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategy;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategyImpl;
import org.datadog.jenkins.plugins.datadog.traces.write.Track;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

/**
 * This class is used to collect all methods that has to do with transmitting
 * data to Datadog.
 */
public class DatadogApiClient implements DatadogClient {

    private static volatile DatadogApiClient instance = null;
    // Used to determine if the instance failed last validation last time, so
    // we do not keep retrying to create the instance and logging the same error
    private static boolean failedLastValidation = false;

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

    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL")
    public static boolean enableValidations = true;

    private final String url;
    private final String logIntakeUrl;
    private final String webhookIntakeUrl;
    private final Secret apiKey;
    private boolean defaultIntakeConnectionBroken = false;
    private boolean logIntakeConnectionBroken = false;
    private boolean webhookIntakeConnectionBroken = false;

    private final HttpClient httpClient;

    /**
     * NOTE: Use ClientFactory.getClient method to instantiate the client in the Jenkins Plugin
     * This method is not recommended to be used because it misses some validations.
     * @param url - target url
     * @param logIntakeUrl - log intake url
     * @param apiKey - Secret api Key
     * @return an singleton instance of the DatadogHttpClient.
     */
    @SuppressFBWarnings(value="DC_DOUBLECHECK")
    public static DatadogClient getInstance(String url, String logIntakeUrl, String webhookIntakeUrl, Secret apiKey){
        // If the configuration has not changed, return the current instance without validation
        // since we've already validated and/or errored about the data
        if (instance != null && !configurationChanged(url, logIntakeUrl, webhookIntakeUrl, apiKey)) {
            if (DatadogApiClient.failedLastValidation) {
                return null;
            }
            return instance;
        }
        DatadogApiClient newInstance = new DatadogApiClient(url, logIntakeUrl, webhookIntakeUrl, apiKey);
        if (enableValidations) {
            synchronized (DatadogApiClient.class) {
                DatadogApiClient.instance = newInstance;
                try {
                    newInstance.validateConfiguration();
                    DatadogApiClient.failedLastValidation = false;
                } catch(IllegalArgumentException e){
                    logger.severe(e.getMessage());
                    DatadogApiClient.failedLastValidation = true;
                    return null;
                }
            }
        }
        return newInstance;
    }

    private static boolean configurationChanged(String url, String logIntakeUrl, String webhookIntakeUrl, Secret apiKey){
        return !instance.getUrl().equals(url) ||
                !instance.getLogIntakeUrl().equals(logIntakeUrl) ||
                !instance.getWebhookIntakeUrl().equals(webhookIntakeUrl) ||
                !instance.getApiKey().equals(apiKey);
    }

    private DatadogApiClient(String url, String logIntakeUrl, String webhookIntakeUrl, Secret apiKey) {
        this.url = url;
        this.apiKey = apiKey;
        this.logIntakeUrl = logIntakeUrl;
        this.webhookIntakeUrl = webhookIntakeUrl;
        this.httpClient = new HttpClient(HTTP_TIMEOUT_MS);
    }

    public void validateConfiguration() throws IllegalArgumentException {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Datadog Target URL is not set properly");
        }
        if (apiKey == null || Secret.toString(apiKey).isEmpty()){
            throw new IllegalArgumentException("Datadog API Key is not set properly");
        }
        if (DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildLogs() ) {
            if (logIntakeUrl == null || logIntakeUrl.isEmpty()) {
                throw new IllegalArgumentException("Datadog Log Intake URL is not set properly");
            }
            try {
                boolean logConnection = validateLogIntakeConnection();
                if (!logConnection) {
                    this.logIntakeConnectionBroken = true;
                    logger.warning("Connection broken, please double check both your Log Intake URL and Key");
                }
            } catch (IOException e) {
                this.logIntakeConnectionBroken = true;
                logger.warning("Connection broken, please double check both your Log Intake URL and Key: " + e);
            }
        }

        if (DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility() ) {
            if (webhookIntakeUrl == null || webhookIntakeUrl.isEmpty()) {
                throw new IllegalArgumentException("Datadog Webhook Intake URL is not set properly");
            }
            try {
                boolean webhookConnection = validateWebhookIntakeConnection();
                if (!webhookConnection) {
                    this.webhookIntakeConnectionBroken = true;
                    logger.warning("Connection broken, please double check both your Webhook Intake URL and Key");
                }
            } catch (IOException e) {
                this.webhookIntakeConnectionBroken = true;
                logger.warning("Connection broken, please double check both your Webhook Intake URL and Key: " + e);
            }
        }

        boolean intakeConnection = validateDefaultIntakeConnection(httpClient, url, apiKey);
        if (!intakeConnection) {
            this.defaultIntakeConnectionBroken = true;
            throw new IllegalArgumentException("Connection broken, please double check both your API URL and Key");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof DatadogApiClient)) {
            return false;
        }

        DatadogApiClient newInstance = (DatadogApiClient) object;

        return StringUtils.equals(getLogIntakeUrl(), newInstance.getLogIntakeUrl())
            && StringUtils.equals(getWebhookIntakeUrl(), newInstance.getWebhookIntakeUrl())
            && StringUtils.equals(getUrl(), newInstance.getUrl())
            && ((newInstance.getApiKey() == null && getApiKey() == null)|| newInstance.getApiKey().equals(getApiKey()));
    }

    @Override
    public int hashCode() {
        int result = apiKey != null ? apiKey.hashCode() : 0;
        result = 43 * result + (url != null ? url.hashCode() : 0);
        result = 43 * result + (logIntakeUrl != null ? logIntakeUrl.hashCode() : 0);
        result = 43 * result + (webhookIntakeUrl != null ? webhookIntakeUrl.hashCode() : 0);
        return result;
    }

    public String getUrl() {
        return url;
    }

    public String getLogIntakeUrl() {
        return logIntakeUrl;
    }

    public String getWebhookIntakeUrl() {
        return webhookIntakeUrl;
    }

    public Secret getApiKey() {
        return apiKey;
    }

    public boolean event(DatadogEvent event) {
        logger.fine("Sending event");
        if(this.defaultIntakeConnectionBroken){
            logger.severe("Your client is not initialized properly");
            return false;
        }

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
    public boolean incrementCounter(String name, String hostname, Map<String, Set<String>> tags) {
        if(this.defaultIntakeConnectionBroken){
            logger.severe("Your client is not initialized properly");
            return false;
        }
        ConcurrentMetricCounters.getInstance().increment(name, hostname, tags);
        return true;
    }

    @Override
    public void flushCounters() {
        ConcurrentMap<CounterMetric, Integer> counters = ConcurrentMetricCounters.getInstance().getAndReset();

        logger.fine("Run flushCounters method");
        try (HttpMetrics metrics = metrics()) {
            // Submit all metrics as gauge
            for (Map.Entry<CounterMetric, Integer> entry : counters.entrySet()) {
                CounterMetric counterMetric = entry.getKey();
                int count = entry.getValue();
                logger.fine("Flushing: " + counterMetric.getMetricName() + " - " + count);

                metrics.rate(
                        counterMetric.getMetricName(), count,
                        counterMetric.getHostname(),
                        counterMetric.getTags());
            }
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to flush counters");
        }
    }

    @Override
    public HttpMetrics metrics() {
        return new HttpMetrics();
    }

    private final class HttpMetrics implements Metrics {
        // when we submit a rate we need to divide the submitted value by the interval (10)
        private static final int RATE_INTERVAL = 10;

        private final JSONArray series = new JSONArray();
        private final long timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

        @Override
        public void gauge(String name, long value, String hostname, Map<String, Set<String>> tags) {
            addMetric(name, value, hostname, tags, "gauge");
        }

        public void rate(String name, float value, String hostname, Map<String, Set<String>> tags) {
            addMetric(name, value, hostname, tags, "rate");
        }

        private void addMetric(String name, float value, String hostname, Map<String, Set<String>> tags, String type) {
            logger.fine(String.format("Sending metric '%s' with value %s", name, value));

            JSONArray point = new JSONArray();
            point.add(timestamp);
            if (type.equals("rate")) {
                point.add(value / (float) RATE_INTERVAL);
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
     * @return a boolean to signify the success or failure of the HTTP POST request.
     */
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    private void postApi(final JSONObject payload, final String type) throws IOException {
        if (this.defaultIntakeConnectionBroken) {
            throw new IOException("HTTP client is not initialized properly");
        }

        String url = getUrl() + type;

        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(apiKey));

        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

        httpClient.postAsynchronously(url, headers, "application/json", body);
    }

    /**
     * Posts a given payload to the Datadog Logs Intake, using the user configured apiKey.
     *
     * @param payload - A String containing a specific subset of a builds metadata.
     * @return a boolean to signify the success or failure of the HTTP POST request.
     */
    public boolean sendLogs(String payload) {
        if(this.logIntakeConnectionBroken){
            logger.severe("Your client is not initialized properly");
            return false;
        }

        if(this.getLogIntakeUrl() == null || this.getLogIntakeUrl().isEmpty()){
            logger.severe("Datadog Log Intake URL is not set properly");
            throw new RuntimeException("Datadog Log Collection Port not set properly");
        }

        return postLogs(payload);
    }

    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    private boolean postLogs(String payload) {
        if(payload == null){
            logger.fine("No payload to post");
            return true;
        }

        String url = getLogIntakeUrl();

        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(apiKey));

        byte[] body = payload.getBytes(StandardCharsets.UTF_8);

        try {
            httpClient.postAsynchronously(url, headers, "application/json", body);
            return true;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to post logs");
            return false;
        }
    }

    public static boolean validateDefaultIntakeConnection(HttpClient client, String validatedUrl, Secret apiKey) {
        String urlParameters = "?api_key=" + Secret.toString(apiKey);
        String url = validatedUrl + VALIDATE + urlParameters;

        try {
            JSONObject json = (JSONObject) client.get(url, Collections.emptyMap(), JSONSerializer::toJSON);
            return json.getBoolean("valid");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Failed to validate webhook connection");
            return false;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to validate webhook connection");
            return false;
        }
    }

    private boolean validateLogIntakeConnection() throws IOException {
        return postLogs("{\"message\":\"[datadog-plugin] Check connection\", " +
                "\"ddsource\":\"Jenkins\", \"service\":\"Jenkins\", " +
                "\"hostname\":\""+DatadogUtilities.getHostname(null)+"\"}");
    }

    @SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
    private boolean validateWebhookIntakeConnection() throws IOException {
        String url = getWebhookIntakeUrl();

        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(apiKey));

        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        try {
            JSON jsonResponse = httpClient.post(url, headers, "application/json", body, JSONSerializer::toJSON);
            // consider test successful if JSON was parsed without errors
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Failed to validate webhook connection");
            return false;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to validate webhook connection");
            return false;
        }
    }

    @Override
    public TraceWriteStrategy createTraceWriteStrategy() {
        return new TraceWriteStrategyImpl(Track.WEBHOOK, this::sendSpans);
    }

    private void sendSpans(Collection<Span> spans) {
        if (this.webhookIntakeConnectionBroken) {
            throw new RuntimeException("Your client is not initialized properly; webhook intake connection is broken.");
        }

        DatadogGlobalConfiguration datadogGlobalDescriptor = DatadogUtilities.getDatadogGlobalDescriptor();
        String urlParameters = datadogGlobalDescriptor != null ? "?service=" + datadogGlobalDescriptor.getCiInstanceName() : "";
        String url = getWebhookIntakeUrl() + urlParameters;

        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(apiKey));
        headers.put("DD-CI-PROVIDER-NAME", "jenkins");

        for (Span span : spans) {
            if (span.getTrack() != Track.WEBHOOK) {
                logger.severe("Expected webhook track, got " + span.getTrack() + ", dropping span");
                continue;
            }

            byte[] body = span.getPayload().toString().getBytes(StandardCharsets.UTF_8);

            // webhook intake does not support batch requests
            logger.fine("Sending webhook");
            httpClient.postAsynchronously(url, headers, "application/json", body);
        }
    }
}
