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
import hudson.util.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookPipelineLogic;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * This class is used to collect all methods that has to do with transmitting
 * data to Datadog.
 */
public class DatadogHttpClient implements DatadogClient {

    private static volatile DatadogHttpClient instance = null;
    // Used to determine if the instance failed last validation last time, so
    // we do not keep retrying to create the instance and logging the same error
    private static boolean failedLastValidation = false;

    private static final Logger logger = Logger.getLogger(DatadogHttpClient.class.getName());

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
    private String jreVersion = null;
    private String jenkinsVersion = null;
    private String pluginVersion = null;


    private String url = null;
    private String logIntakeUrl = null;
    private String webhookIntakeUrl = null;
    private Secret apiKey = null;
    private boolean defaultIntakeConnectionBroken = false;
    private boolean logIntakeConnectionBroken = false;
    private boolean webhookIntakeConnectionBroken = false;
    private DatadogWebhookBuildLogic webhookBuildLogic;
    private DatadogWebhookPipelineLogic webhookPipelineLogic;

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

        DatadogHttpClient newInstance = new DatadogHttpClient(url, logIntakeUrl, webhookIntakeUrl, apiKey);
        if (instance != null && instance.equals(newInstance)) {
            if (DatadogHttpClient.failedLastValidation) {
                return null;
            }
            return instance;
        }
        if (enableValidations) {
            synchronized (DatadogHttpClient.class) {
                DatadogHttpClient.instance = newInstance;
                try {
                    newInstance.validateConfiguration();
                    DatadogHttpClient.failedLastValidation = false;
                } catch(IllegalArgumentException e){
                    logger.severe(e.getMessage());
                    DatadogHttpClient.failedLastValidation = true;
                    return null;
                }
            }
        }
        return newInstance;
    }

    private DatadogHttpClient(String url, String logIntakeUrl, String webhookIntakeUrl, Secret apiKey) {
        this.url = url;
        this.apiKey = apiKey;
        this.logIntakeUrl = logIntakeUrl;
        this.webhookIntakeUrl = webhookIntakeUrl;
        this.webhookBuildLogic = new DatadogWebhookBuildLogic(this);
        this.webhookPipelineLogic = new DatadogWebhookPipelineLogic(this);
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
                    instance.setLogIntakeConnectionBroken(true);
                    logger.warning("Connection broken, please double check both your Log Intake URL and Key");
                }
            } catch (IOException e) {
                instance.setLogIntakeConnectionBroken(true);
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
                    instance.setWebhookIntakeConnectionBroken(true);
                    logger.warning("Connection broken, please double check both your Webhook Intake URL and Key");
                }
            } catch (IOException e) {
                instance.setWebhookIntakeConnectionBroken(true);
                logger.warning("Connection broken, please double check both your Webhook Intake URL and Key: " + e);
            }
        }

        boolean intakeConnection = validateDefaultIntakeConnection(httpClient, url, apiKey);
        if (!intakeConnection) {
            instance.setDefaultIntakeConnectionBroken(true);
            throw new IllegalArgumentException("Connection broken, please double check both your API URL and Key");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof DatadogHttpClient)) {
            return false;
        }

        DatadogHttpClient newInstance = (DatadogHttpClient) object;

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

    @Override
    public void setUrl(String url) {
        this.url = url;
    }

    public String getLogIntakeUrl() {
        return logIntakeUrl;
    }

    @Override
    public void setLogIntakeUrl(String logIntakeUrl) {
        this.logIntakeUrl = logIntakeUrl;
    }

    public String getWebhookIntakeUrl() {
        return webhookIntakeUrl;
    }

    @Override
    public void setWebhookIntakeUrl(String webhookIntakeUrl) {
        this.webhookIntakeUrl = webhookIntakeUrl;
    }

    public Secret getApiKey() {
        return apiKey;
    }

    @Override
    public void setApiKey(Secret apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void setHostname(String hostname) {
        // noop
    }

    @Override
    public void setPort(Integer port) {
        // noop
    }

    @Override
    public void setLogCollectionPort(Integer logCollectionPort) {
        // noop
    }

    @Override
    public boolean isDefaultIntakeConnectionBroken() {
        return defaultIntakeConnectionBroken;
    }

    @Override
    public void setDefaultIntakeConnectionBroken(boolean defaultIntakeConnectionBroken) {
        this.defaultIntakeConnectionBroken = defaultIntakeConnectionBroken;
    }

    @Override
    public boolean isLogIntakeConnectionBroken() {
        return logIntakeConnectionBroken;
    }

    @Override
    public void setLogIntakeConnectionBroken(boolean logIntakeConnectionBroken) {
        this.logIntakeConnectionBroken = logIntakeConnectionBroken;
    }

    @Override
    public boolean isWebhookIntakeConnectionBroken() {
        return webhookIntakeConnectionBroken;
    }

    @Override
    public void setWebhookIntakeConnectionBroken(boolean webhookIntakeConnectionBroken) {
        this.webhookIntakeConnectionBroken = webhookIntakeConnectionBroken;
    }

    public boolean event(DatadogEvent event) {
        logger.fine("Sending event");
        if(this.isDefaultIntakeConnectionBroken()){
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
        if(this.isDefaultIntakeConnectionBroken()){
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
        // Submit all metrics as gauge
        for (Map.Entry<CounterMetric, Integer> entry : counters.entrySet()) {
            CounterMetric counterMetric = entry.getKey();
            int count = entry.getValue();
            logger.fine("Flushing: " + counterMetric.getMetricName() + " - " + count);

            try {
                // Since we submit a rate we need to divide the submitted value by the interval (10)
                this.postMetric(
                        counterMetric.getMetricName(), count,
                        counterMetric.getHostname(),
                        counterMetric.getTags(),
                        "rate");
            } catch (IOException e) {
                DatadogUtilities.severe(logger, e, "Failed to flush counters");
            }
        }
    }

    @Override
    public boolean gauge(String name, long value, String hostname, Map<String, Set<String>> tags) {
        try {
            postMetric(name, value, hostname, tags, "gauge");
            return true;
        } catch (IOException e) {
            DatadogUtilities.severe(logger, e, "Failed to send gauge");
            return false;
        }
    }

    private void postMetric(String name, float value, String hostname, Map<String, Set<String>> tags, String type) throws IOException {
        if (this.isDefaultIntakeConnectionBroken()) {
            throw new IOException("Your client is not initialized properly");
        }

        int INTERVAL = 10;

        logger.fine(String.format("Sending metric '%s' with value %s", name, String.valueOf(value)));

        // Setup data point, of type [<unix_timestamp>, <value>]
        JSONArray points = new JSONArray();
        JSONArray point = new JSONArray();

        point.add(System.currentTimeMillis() / 1000); // current time, s
        if(type.equals("rate")){
            point.add(value / (float)INTERVAL);
        } else {
            point.add(value);
        }
        points.add(point); // api expects a list of points

        JSONObject metric = new JSONObject();
        metric.put("metric", name);
        metric.put("points", points);
        metric.put("type", type);
        metric.put("host", hostname);
        if(type.equals("rate")){
            metric.put("interval", INTERVAL);
        }
        if (tags != null) {
            logger.fine(tags.toString());
            metric.put("tags", TagsUtil.convertTagsToJSONArray(tags));
        }
        // Place metric as item of series list
        JSONArray series = new JSONArray();
        series.add(metric);

        // Add series to payload
        JSONObject payload = new JSONObject();
        payload.put("series", series);

        logger.fine(String.format("payload: %s", payload.toString()));
        postApi(payload, METRIC);
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
        if (this.isDefaultIntakeConnectionBroken()) {
            throw new IOException("HTTP client is not initialized properly");
        }

        String url = getUrl() + type;

        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(apiKey));
        headers.put("User-Agent", String.format("Datadog/%s/jenkins Java/%s Jenkins/%s",
                getDatadogPluginVersion(),
                getJavaRuntimeVersion(),
                getJenkinsVersion()));

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
        if(this.isLogIntakeConnectionBroken()){
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
        headers.put("User-Agent", String.format("Datadog/%s/jenkins Java/%s Jenkins/%s",
                getDatadogPluginVersion(),
                getJavaRuntimeVersion(),
                getJenkinsVersion()));

        byte[] body = payload.getBytes(StandardCharsets.UTF_8);

        try {
            httpClient.postAsynchronously(url, headers, "application/json", body);
            return true;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to post logs");
            return false;
        }
    }

    /**
     * Posts a given payload to the Datadog Webhook Intake, using the user configured apiKey.
     *
     * @param payload - A webhooks payload.
     * @return a boolean to signify the success or failure of the HTTP POST request.
     */
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    @Override
    public boolean postWebhook(String payload) {
        logger.fine("Sending webhook");
        if(this.isWebhookIntakeConnectionBroken()){
            logger.severe("Your client is not initialized properly; webhook intake connection is broken.");
            return false;
        }

        DatadogGlobalConfiguration datadogGlobalDescriptor = DatadogUtilities.getDatadogGlobalDescriptor();
        String urlParameters = datadogGlobalDescriptor != null ? "?service=" + datadogGlobalDescriptor.getCiInstanceName() : "";
        String url = getWebhookIntakeUrl() + urlParameters;

        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(apiKey));
        headers.put("DD-CI-PROVIDER-NAME", "jenkins");
        headers.put("User-Agent", String.format("Datadog/%s/jenkins Java/%s Jenkins/%s",
                getDatadogPluginVersion(),
                getJavaRuntimeVersion(),
                getJenkinsVersion()));

        byte[] body = payload.getBytes(StandardCharsets.UTF_8);

        try {
            httpClient.postAsynchronously(url, headers, "application/json", body);
            return true;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to post webhook");
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

    public boolean validateLogIntakeConnection() throws IOException {
        return postLogs("{\"message\":\"[datadog-plugin] Check connection\", " +
                "\"ddsource\":\"Jenkins\", \"service\":\"Jenkins\", " +
                "\"hostname\":\""+DatadogUtilities.getHostname(null)+"\"}");
    }

    @SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
    public boolean validateWebhookIntakeConnection() throws IOException {
        String url = getWebhookIntakeUrl();

        Map<String, String> headers = new HashMap<>();
        headers.put("DD-API-KEY", Secret.toString(apiKey));
        headers.put("User-Agent", String.format("Datadog/%s/jenkins Java/%s Jenkins/%s",
                getDatadogPluginVersion(),
                getJavaRuntimeVersion(),
                getJenkinsVersion()));

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

    private String getJavaRuntimeVersion(){
        if(this.jreVersion == null) {
            this.jreVersion =  System.getProperty("java.version");
        }
        return this.jreVersion;
    }

    private String getDatadogPluginVersion(){
        if(this.pluginVersion == null){
            this.pluginVersion = this.getClass().getPackage().getImplementationVersion();
        }
        return this.pluginVersion;
    }

    private String getJenkinsVersion(){
        if(this.jenkinsVersion == null) {
            this.jenkinsVersion =  Jenkins.VERSION;
        }
        return this.jenkinsVersion;
    }

    @Override
    public boolean startBuildTrace(BuildData buildData, Run run) {
        if(this.isWebhookIntakeConnectionBroken()){
            logger.severe("Unable to start build trace; your client is not initialized properly.");
            return false;
        }
        try {
            logger.fine("Started build trace");
            this.webhookBuildLogic.startBuildTrace(buildData, run);
            return true;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to start build trace");
            return false;
        }
    }

    @Override
    public boolean finishBuildTrace(BuildData buildData, Run<?, ?> run) {
        if(this.isWebhookIntakeConnectionBroken()){
            logger.severe("Unable to finish build trace; your client is not initialized properly.");
            return false;
        }
        try {
            logger.fine("Finished build trace");
            this.webhookBuildLogic.finishBuildTrace(buildData, run);
            return true;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to finish build trace");
            return false;
        }
    }

    @Override
    public boolean sendPipelineTrace(Run<?, ?> run, FlowNode flowNode) {
        if(this.isWebhookIntakeConnectionBroken()){
            logger.severe("Unable to send pipeline trace; your client is not initialized properly");
            return false;
        }
        try {
            logger.fine("Send pipeline traces.");
            this.webhookPipelineLogic.execute(run, flowNode);
            return true;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to send pipeline trace");
            return false;
        }
    }
}
