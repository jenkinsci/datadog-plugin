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

import hudson.ProxyConfiguration;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
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

    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL")
    public static boolean enableValidations = true;
    private String jreVersion = null;
    private String jenkinsVersion = null;
    private String pluginVersion = null;


    private String url = null;
    private String logIntakeUrl = null;
    private Secret apiKey = null;
    private boolean defaultIntakeConnectionBroken = false;
    private boolean logIntakeConnectionBroken = false;

    /**
     * NOTE: Use ClientFactory.getClient method to instantiate the client in the Jenkins Plugin
     * This method is not recommended to be used because it misses some validations.
     * @param url - target url
     * @param logIntakeUrl - log intake url
     * @param apiKey - Secret api Key
     * @return an singleton instance of the DatadogHttpClient.
     */
    @SuppressFBWarnings(value="DC_DOUBLECHECK")
    public static DatadogClient getInstance(String url, String logIntakeUrl, Secret apiKey){
        // If the configuration has not changed, return the current instance without validation
        // since we've already validated and/or errored about the data

        DatadogHttpClient newInstance = new DatadogHttpClient(url, logIntakeUrl, apiKey);
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

    private DatadogHttpClient(String url, String logIntakeUrl, Secret apiKey) {
        this.url = url;
        this.apiKey = apiKey;
        this.logIntakeUrl = logIntakeUrl;
    }

    public void validateConfiguration() throws IllegalArgumentException {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Datadog Target URL is not set properly");
        }
        if (apiKey == null || Secret.toString(apiKey).isEmpty()){
            throw new IllegalArgumentException("Datadog API Key is not set properly");
        }
        if (DatadogHttpClient.isCollectBuildLogEnabled() && (logIntakeUrl == null || logIntakeUrl.isEmpty())){
            throw new IllegalArgumentException("Datadog Log Intake URL is not set properly");
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
        if (DatadogHttpClient.isCollectBuildLogEnabled()) {
            try {
                boolean logConnection = validateLogIntakeConnection(url, apiKey);
                if (!logConnection) {
                    instance.setLogIntakeConnectionBroken(true);
                    logger.warning("Connection broken, please double check both your Log Intake URL and Key");
                }
            } catch (IOException e) {
                instance.setLogIntakeConnectionBroken(true);
                logger.warning("Connection broken, please double check both your Log Intake URL and Key");
            }
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

        if ((StringUtils.equals(getLogIntakeUrl(), newInstance.getLogIntakeUrl()))
        && (StringUtils.equals(getUrl(), newInstance.getUrl())
        && ((newInstance.getApiKey() == null && getApiKey() == null) || newInstance.getApiKey().equals(getApiKey())))){
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = apiKey != null ? apiKey.hashCode() : 0;
        result = 43 * result + (url != null ? url.hashCode() : 0);
        result = 43 * result + (logIntakeUrl != null ? logIntakeUrl.hashCode() : 0);
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

    public boolean event(DatadogEvent event) {
        logger.fine("Sending event");
        boolean status;
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
            status = post(payload, EVENT);
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
            status = false;
        }
        return status;
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
            status = post(payload, METRIC);
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, null);
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

        return post(payload, SERVICECHECK);
    }

    /**
     * Posts a given {@link JSONObject} payload to the Datadog API, using the
     * user configured apiKey.
     *
     * @param payload - A JSONObject containing a specific subset of a builds metadata.
     * @param type    - A String containing the URL subpath pertaining to the type of API post required.
     * @return a boolean to signify the success or failure of the HTTP POST request.
     */
    private boolean post(final JSONObject payload, final String type) {
        if(this.isDefaultIntakeConnectionBroken()){
            logger.severe("Your client is not initialized properly");
            return false;
        }

        String urlParameters = "?api_key=" + Secret.toString(this.getApiKey());
        HttpURLConnection conn = null;
        boolean status = true;

        try {
            logger.fine("Setting up HttpURLConnection...");
            conn = getHttpURLConnection(new URL(this.getUrl() + type + urlParameters));
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

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
                    DatadogUtilities.severe(logger, e, null);
                } else {
                    DatadogUtilities.severe(logger, e, "Unknown client error, please check your config");
                }
            } catch (IOException ex) {
                DatadogUtilities.severe(logger, e, null);
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
     * Posts a given {@link JSONObject} payload to the Datadog API, using the
     * user configured apiKey.
     *
     * @param payload - A JSONObject containing a specific subset of a builds metadata.
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

        return postLogs(this.getLogIntakeUrl(), getApiKey(), payload);
    }

    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    private boolean postLogs(String url, Secret apiKey, String payload) {
        if(payload == null){
            logger.fine("No payload to post");
            return true;
        }

        HttpURLConnection conn = null;
        try {
            URL logsEndpointURL = new URL(url);
            logger.fine("Setting up HttpURLConnection...");
            conn = getHttpURLConnection(logsEndpointURL);
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
                DatadogUtilities.severe(logger, ex, null);
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
     * Returns an HTTP url connection given a url object. Supports jenkins configured proxy.
     *
     * @param url - a URL object containing the URL to open a connection to.
     * @return a HttpURLConnection object.
     * @throws IOException if HttpURLConnection fails to open connection
     */
    private static HttpURLConnection getHttpURLConnection(final URL url) throws IOException {
        HttpURLConnection conn = null;
        ProxyConfiguration proxyConfig = null;

        Jenkins jenkins = Jenkins.getInstance();
        if(jenkins != null){
            proxyConfig = jenkins.proxy;
        }

        /* Attempt to use proxy */
        if (proxyConfig != null) {
            Proxy proxy = proxyConfig.createProxy(url.getHost());
            if (proxy != null && proxy.type() == Proxy.Type.HTTP) {
                logger.fine("Attempting to use the Jenkins proxy configuration");
                conn = (HttpURLConnection) url.openConnection(proxy);
            }
        } else {
            logger.fine("Jenkins proxy configuration not found");
        }

        /* If proxy fails, use HttpURLConnection */
        if (conn == null) {
            conn = (HttpURLConnection) url.openConnection();
            logger.fine("Using HttpURLConnection, without proxy");
        }

        /* Timeout of 1 minutes for connecting and reading.
        * this prevents this plugin from causing jobs to hang in case of
        * flaky network or Datadog being down. Left intentionally long.
        */
        int timeoutMS = 1 * 60 * 1000;
        conn.setConnectTimeout(timeoutMS);
        conn.setReadTimeout(timeoutMS);

        return conn;
    }

    public static boolean validateDefaultIntakeConnection(String url, Secret apiKey) throws IOException {
        String urlParameters = "?api_key=" + Secret.toString(apiKey);
        HttpURLConnection conn = null;
        boolean status = true;
        try {
            // Make request
            conn = getHttpURLConnection(new URL(url + VALIDATE + urlParameters));
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

    public static boolean validateLogIntakeConnection(String url, Secret apiKey) throws IOException {
        DatadogHttpClient client = new DatadogHttpClient(null, url, apiKey);
        return client.postLogs(url, apiKey, "{\"message\":\"[datadog-plugin] Check connection\", " +
                "\"ddsource\":\"Jenkins\", \"service\":\"Jenkins\", " +
                "\"hostname\":\""+DatadogUtilities.getHostname(null)+"\"}");
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

    private static boolean isCollectBuildLogEnabled(){
        return DatadogUtilities.getDatadogGlobalDescriptor() != null &&
                DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildLogs();
    }

}
