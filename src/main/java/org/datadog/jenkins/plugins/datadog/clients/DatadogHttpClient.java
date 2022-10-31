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

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getHttpURLConnection;

import hudson.model.Run;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookPipelineLogic;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * This class is used to collect all methods that has to do with transmitting
 * data to Datadog.
 */
public class DatadogHttpClient implements DatadogClient {

    private static DatadogHttpClient instance = null;
    // Used to determine if the instance failed last validation last time, so
    // we do not keep retrying to create the instance and logging the same error
    private static boolean failedLastValidation = false;

    private static final Logger logger = Logger.getLogger(DatadogHttpClient.class.getName());

    private static final String EVENT = "v1/events";
    private static final String METRIC = "v1/series";
    private static final String SERVICECHECK = "v1/check_run";
    private static final String VALIDATE = "v1/validate";

    private static final Integer HTTP_FORBIDDEN = 403;
    private static final Integer BAD_REQUEST = 400;

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

        try {
            boolean intakeConnection = validateDefaultIntakeConnection(url, apiKey);
            if (!intakeConnection) {
                instance.setDefaultIntakeConnectionBroken(true);

                throw new IllegalArgumentException("Connection broken, please double check both your API URL and Key");
            }
        } catch (IOException e) {
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
            return postApi(payload, EVENT);
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
        for(final Iterator<Map.Entry<CounterMetric, Integer>> iter = counters.entrySet().iterator(); iter.hasNext();){
            Map.Entry<CounterMetric, Integer> entry = iter.next();
            CounterMetric counterMetric = entry.getKey();
            int count = entry.getValue();
            logger.fine("Flushing: " + counterMetric.getMetricName() + " - " + count);
            // Since we submit a rate we need to divide the submitted value by the interval (10)
            this.postMetric(counterMetric.getMetricName(), count, counterMetric.getHostname(),
                    counterMetric.getTags(), "rate");

        }
    }

    @Override
    public boolean gauge(String name, long value, String hostname, Map<String, Set<String>> tags) {
        return postMetric(name, value, hostname, tags, "gauge");
    }

    private boolean postMetric(String name, float value, String hostname, Map<String, Set<String>> tags, String type) {
        if(this.isDefaultIntakeConnectionBroken()){
            logger.severe("Your client is not initialized properly");
            return false;
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

        boolean status;
        try {
            status = postApi(payload, METRIC);
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to send metric payload");
            status = false;
        }
        return status;
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

        return postApi(payload, SERVICECHECK);
    }

    /**
     * Configures a HttpURLConnection to send a json POST request to the given Datadog API url.
     *
     * @param url - A Datadog API endpoint
     */
    HttpURLConnection createApiPostConnection(URL url) throws IOException {
        HttpURLConnection conn = getHttpURLConnection(url, HTTP_TIMEOUT_MS);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("DD-API-KEY", Secret.toString(apiKey));
        conn.setRequestProperty("User-Agent", String.format("Datadog/%s/jenkins Java/%s Jenkins/%s",
                getDatadogPluginVersion(),
                getJavaRuntimeVersion(),
                getJenkinsVersion()));
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        return conn;
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
    private boolean postApi(final JSONObject payload, final String type) {
        if(this.isDefaultIntakeConnectionBroken()){
            logger.severe("Your client is not initialized properly");
            return false;
        }

        HttpURLConnection conn = null;
        boolean status = true;

        try {
            logger.fine("Setting up HttpURLConnection...");
            conn = createApiPostConnection(new URL(this.getUrl() + type));

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "utf-8");
            logger.fine("Writing to OutputStreamWriter...");
            wr.write(payload.toString());
            wr.close();

            // Get response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();
            JSONObject json = (JSONObject) JSONSerializer.toJSON(result.toString());
            if ("ok".equals(json.getString("status"))) {
                logger.fine(String.format("API call of type '%s' was sent successfully!", type));
                logger.fine(String.format("Payload: %s", payload));
            } else {
                logger.severe(String.format("API call of type '%s' failed!", type));
                logger.fine(String.format("Payload: %s", payload));
                status = false;
            }
        } catch (Exception e) {
            try {
                if (conn != null && conn.getResponseCode() == HTTP_FORBIDDEN) {
                    logger.severe("Hmmm, your API key may be invalid. We received a 403 error.");
                    DatadogUtilities.severe(logger, e, "API key is invalid, please check your config");
                } else {
                    DatadogUtilities.severe(logger, e, "Unknown client error, please check your config");
                }
            } catch (IOException ex) {
                DatadogUtilities.severe(logger, e, "Failed to inspect HTTP response");
            }
            status = false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return status;
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

        HttpURLConnection conn = null;
        try {
            logger.fine("Setting up HttpURLConnection...");
            conn = createApiPostConnection(new URL(this.getLogIntakeUrl()));

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "utf-8");
            logger.fine("Writing to OutputStreamWriter...");
            wr.write(payload);
            wr.close();

            // Get response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();

            if ("{}".equals(result.toString())) {
                logger.fine(String.format("Logs API call was sent successfully!"));
                logger.fine(String.format("Payload: %s", payload));
            } else {
                logger.severe(String.format("Logs API call failed!"));
                logger.fine(String.format("Payload: %s", payload));
                return false;
            }
        } catch (Exception e) {
            try {
                if (conn != null && conn.getResponseCode() == BAD_REQUEST) {
                    logger.severe("Hmmm, your API key or your Log Intake URL may be invalid. We received a 400 in response.");
                } else {
                    DatadogUtilities.severe(logger, e, "Unknown client error, please check your config");
                }
            } catch (IOException ex) {
                DatadogUtilities.severe(logger, ex, "Failed to inspect HTTP response");
            }
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return true;
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

        HttpURLConnection conn = null;
        try {
            logger.fine("Setting up HttpURLConnection...");
            String urlParameters = "?service=" + DatadogUtilities.getDatadogGlobalDescriptor().getCiInstanceName();
            conn = createApiPostConnection(new URL(this.getWebhookIntakeUrl() + urlParameters));
            conn.setRequestProperty("DD-CI-PROVIDER-NAME", "jenkins");

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "utf-8");
            logger.fine("Writing to OutputStreamWriter...");
            wr.write(payload);
            wr.close();

            // Get response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();

            if ("{}".equals(result.toString())) {
                logger.fine(String.format("Logs API call was sent successfully!"));
                logger.fine(String.format("Payload: %s", payload));
            } else {
                logger.severe(String.format("Logs API call failed!"));
                logger.fine(String.format("Payload: %s", payload));
                return false;
            }
        } catch (Exception e) {
            try {
                if (conn != null && conn.getResponseCode() == BAD_REQUEST) {
                    logger.severe("Hmmm, your API key or your Webhook Intake URL may be invalid. We received a 400 in response.");
                } else {
                    DatadogUtilities.severe(logger, e, "Unknown client error, please check your config");
                }
            } catch (IOException ex) {
                DatadogUtilities.severe(logger, ex, "Failed to inspect HTTP response");
            }
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return true;
    }

    public static boolean validateDefaultIntakeConnection(String url, Secret apiKey) throws IOException {
        String urlParameters = "?api_key=" + Secret.toString(apiKey);
        HttpURLConnection conn = null;
        boolean status = true;
        try {
            // Make request
            conn = getHttpURLConnection(new URL(url + VALIDATE + urlParameters), HTTP_TIMEOUT_MS);
            conn.setRequestMethod("GET");

            // Get response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();

            // Validate
            JSONObject json = (JSONObject) JSONSerializer.toJSON(result.toString());
            if (!json.getBoolean("valid")) {
                status = false;
            }
        } catch (Exception e) {
            if (conn != null && conn.getResponseCode() == HTTP_FORBIDDEN) {
                logger.severe("Hmmm, your API key may be invalid. We received a 403 error.");
            } else {
                DatadogUtilities.severe(logger, e, "Unknown client error, please check your config");
            }
            status = false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return status;
    }

    public boolean validateLogIntakeConnection() throws IOException {
        return postLogs("{\"message\":\"[datadog-plugin] Check connection\", " +
                "\"ddsource\":\"Jenkins\", \"service\":\"Jenkins\", " +
                "\"hostname\":\""+DatadogUtilities.getHostname(null)+"\"}");
    }

    public boolean validateWebhookIntakeConnection() throws IOException {
        HttpURLConnection conn = null;
        boolean status = true;
        try {
            // Make request
            conn = createApiPostConnection(new URL(this.getWebhookIntakeUrl()));

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "utf-8");
            wr.write("{}");
            wr.close();

            // Get response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();

            // Validate response
            JSONSerializer.toJSON(result.toString()); // throws if response is not json
        } catch (Exception e) {
            if (conn != null && conn.getResponseCode() == HTTP_FORBIDDEN) {
                logger.severe("Hmmm, your API key may be invalid. We received a 403 error.");
            } else {
                DatadogUtilities.severe(logger, e, "Unknown client error, please check your config");
            }
            status = false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return status;
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
