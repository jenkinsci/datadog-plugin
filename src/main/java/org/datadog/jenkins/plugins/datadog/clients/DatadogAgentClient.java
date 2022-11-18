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

import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.buildHttpURL;
import static org.datadog.jenkins.plugins.datadog.DatadogUtilities.getHttpURLConnection;
import static org.datadog.jenkins.plugins.datadog.transport.LoggerHttpErrorHandler.LOGGER_HTTP_ERROR_HANDLER;

import com.timgroup.statsd.Event;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.ServiceCheck;
import com.timgroup.statsd.StatsDClient;
import hudson.model.Run;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.traces.DatadogBaseBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogBasePipelineLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTraceBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogTracePipelineLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookBuildLogic;
import org.datadog.jenkins.plugins.datadog.traces.DatadogWebhookPipelineLogic;
import org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper;
import org.datadog.jenkins.plugins.datadog.transport.HttpClient;
import org.datadog.jenkins.plugins.datadog.transport.HttpMessage;
import org.datadog.jenkins.plugins.datadog.transport.HttpMessageFactory;
import org.datadog.jenkins.plugins.datadog.transport.NonBlockingHttpClient;
import org.datadog.jenkins.plugins.datadog.transport.PayloadMessage;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jfree.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SocketHandler;

/**
 * This class is used to collect all methods that has to do with transmitting
 * data to Datadog.
 */
public class DatadogAgentClient implements DatadogClient {

    private static DatadogAgentClient instance = null;
    // Used to determine if the instance failed last validation last time, so
    // we do not keep retrying to create the instance and logging the same error
    private static boolean failedLastValidation = false;

    private static final Logger logger = Logger.getLogger(DatadogAgentClient.class.getName());

    private static final Integer BAD_REQUEST = 400;
    private static final Integer NOT_FOUND = 404;

    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL")
    public static boolean enableValidations = true;

    private HttpClient agentHttpClient;
    private DatadogBaseBuildLogic traceBuildLogic;
    private DatadogBasePipelineLogic tracePipelineLogic;

    private StatsDClient statsd;
    private Logger ddLogger;
    private String previousPayload;

    private String hostname = null;
    private String resolvedIp = "";
    private Integer port = null;
    private Integer logCollectionPort = null;
    private Integer traceCollectionPort = null;
    private boolean isStoppedStatsDClient = true;
    private boolean isStoppedAgentHttpClient = true;
    private boolean evpProxySupported = false;
    private long lastEvpProxyCheckTimeMs = 0L;

    /**
     * How often to check the /info endpoint in case the Agent got updated.
     */
    private static final int EVP_PROXY_SUPPORT_TIME_BETWEEN_CHECKS_MS = 1*60*60*1000;

    /**
     * Timeout waiting for a reply after a connection to the /info endpoint was established.
     */
    private static final int HTTP_TIMEOUT_INFO_MS = 10 * 1000;

    /**
     * Timeout of 1 minutes for connecting and reading via the synchronous Agent EVP Proxy.
     * this prevents this plugin from causing jobs to hang in case of
     * flaky network or Datadog being down. Left intentionally long.
     */
    private static final int HTTP_TIMEOUT_EVP_PROXY_MS = 60 * 1000;

    /**
     * NOTE: Use ClientFactory.getClient method to instantiate the client in the Jenkins Plugin
     * This method is not recommended to be used because it misses some validations.
     * @param hostname - target hostname
     * @param port - target port
     * @param logCollectionPort - target log collection port
     * @param traceCollectionPort - target trace collection port
     * @return an singleton instance of the DogStatsDClient.
     */
    @SuppressFBWarnings(value={"DC_DOUBLECHECK", "RC_REF_COMPARISON"})
    public static DatadogClient getInstance(String hostname, Integer port, Integer logCollectionPort, Integer traceCollectionPort){
        // If the configuration has not changed, return the current instance without validation
        // since we've already validated and/or errored about the data

        DatadogAgentClient newInstance = new DatadogAgentClient(hostname, port, logCollectionPort, traceCollectionPort);
        if (instance != null && instance.equals(newInstance)) {
            if (DatadogAgentClient.failedLastValidation) {
                return null;
            }
            return instance;
        }

        synchronized (DatadogAgentClient.class) {
            DatadogAgentClient.instance = newInstance;
            if (enableValidations) {
                try {
                    newInstance.validateConfiguration();
                    DatadogAgentClient.failedLastValidation = false;
                } catch(IllegalArgumentException e){
                    logger.severe(e.getMessage());
                    DatadogAgentClient.failedLastValidation = true;
                    return null;
                }
            }
        }
        if (instance != null){
            instance.reinitializeStatsDClient(true);
            instance.reinitializeLogger(true);
            instance.reinitializeAgentHttpClient(true);
        }
        return instance;
    }

    protected DatadogAgentClient(String hostname, Integer port, Integer logCollectionPort, Integer traceCollectionPort) {
        this.hostname = hostname;
        this.port = port;
        this.logCollectionPort = logCollectionPort;
        this.traceCollectionPort = traceCollectionPort;
    }

    public static ConnectivityResult checkConnectivity(final String host, final int port) {
        try(Socket ignored = new Socket(host, port)) {
            return ConnectivityResult.SUCCESS;
        } catch (Exception ex) {
            DatadogUtilities.severe(logger, ex, "Failed to create socket to host: " + host + ", port: " +port + ". Error: " + ex);
            return new ConnectivityResult(true, ex.toString());
        }
    }

    public void validateConfiguration() throws IllegalArgumentException {
        if (hostname == null || hostname.isEmpty()) {
            throw new IllegalArgumentException("Datadog Target URL is not set properly");
        }
        if (port == null) {
            throw new IllegalArgumentException("Datadog Target Port is not set properly");
        }
        if (DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildLogs()  && logCollectionPort == null) {
            logger.warning("Datadog Log Collection Port is not set properly");
        }

        if (DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()  && traceCollectionPort == null) {
            logger.warning("Datadog Trace Collection Port is not set properly");
        }
        return;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof DatadogAgentClient)) {
            return false;
        }

        DatadogAgentClient newInstance = (DatadogAgentClient) object;

        if ((StringUtils.equals(getHostname(), newInstance.getHostname())
        && (((getPort() == null) && (newInstance.getPort() == null)) || (null != getPort() && port.equals(newInstance.getPort())))
        && (((getLogCollectionPort() == null) && (newInstance.getLogCollectionPort() == null)) || (null != getLogCollectionPort() && logCollectionPort.equals(newInstance.getLogCollectionPort())))
        && (((getTraceCollectionPort() == null) && (newInstance.getTraceCollectionPort() == null)) || (null != getTraceCollectionPort() && traceCollectionPort.equals(newInstance.getTraceCollectionPort())))
        )){
           return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = hostname != null ? hostname.hashCode() : 0;
        result = 47 * result + (port != null ? port.hashCode() : 0);
        result = 47 * result + (logCollectionPort != null ? logCollectionPort.hashCode() : 0);
        result = 47 * result + (traceCollectionPort != null ? traceCollectionPort.hashCode() : 0);
        return result;
    }

    /**
     * reinitialize the dogStatsD Client
     * @param force - force to reinitialize
     * @return true if reinitialized properly otherwise false
     */
    private boolean reinitializeStatsDClient(boolean force) {
        try {
            boolean refreshClient = DatadogUtilities.getDatadogGlobalDescriptor().isRefreshDogstatsdClient();
            if(!this.isStoppedStatsDClient && this.statsd != null && !force && (!refreshClient || !this.hasIpChanged())){
                return true;
            }
            this.stopStatsDClient();
            logger.info("Re/Initialize DogStatsD Client: hostname = " + this.hostname + ", port = " + this.port);
            this.statsd = new NonBlockingStatsDClient(null, this.hostname, this.port);
            this.isStoppedStatsDClient = false;
        } catch (Exception e){
            DatadogUtilities.severe(logger, e, "Failed to reinitialize DogStatsD Client");
            this.stopStatsDClient();
        }

        return !isStoppedStatsDClient;
    }

    private String resolveHostnameIp() throws UnknownHostException {
        InetAddress inet = InetAddress.getByName(this.hostname);
        String ipAddress = inet.getHostAddress();
        return ipAddress;
    }

    private boolean hasIpChanged() throws UnknownHostException {
        String ipAddress = this.resolveHostnameIp();
        if (this.resolvedIp.equals(ipAddress)) {
            return false;
        } else {
            this.resolvedIp = ipAddress;
            return true;
        }
    }

    /**
     * reinitialize the Logger Client
     * @param force - force to reinitialize
     * @return true if reinitialized properly otherwise false
     */
    private boolean reinitializeLogger(boolean force) {
        if(this.ddLogger != null && !force){
            return true;
        }
        if(!DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildLogs() || this.logCollectionPort == null){
            return false;
        }
        try {
            logger.info("Re/Initialize Datadog-Plugin Logger: hostname = " + this.hostname + ", logCollectionPort = " + this.logCollectionPort);
            this.ddLogger = Logger.getLogger("Datadog-Plugin Logger");
            this.ddLogger.setUseParentHandlers(false);
            //Remove all existing Handlers
            Handler[] handlers = this.ddLogger.getHandlers();

            if (handlers != null) {
                for(Handler h : handlers){
                    this.ddLogger.removeHandler(h);
                }
            }
            //Add New Handler
            SocketHandler socketHandler = new SocketHandler(this.hostname, this.logCollectionPort);
            socketHandler.setFormatter(new DatadogFormatter());
            socketHandler.setErrorManager(new DatadogErrorManager());
            this.ddLogger.addHandler(socketHandler);
        } catch (Exception e){
            if(e instanceof UnknownHostException){
                DatadogUtilities.severe(logger, e, "Failed to reinitialize Datadog-Plugin Logger, Unknown Host " + this.hostname);
            }else if(e instanceof ConnectException){
                DatadogUtilities.severe(logger, e, "Failed to reinitialize Datadog-Plugin Logger, Connection exception. This may be because your port is incorrect " + this.logCollectionPort);
            }else{
                DatadogUtilities.severe(logger, e, "Failed to reinitialize Datadog-Plugin Logger");
            }
            return false;
        }
        return true;
    }

    /**
     * Fetches the supported endpoints from the Trace Agent /info API
     *
     * @return a list of endpoints (if /info wasn't available, it will be empty)
     */
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    public Set<String> fetchAgentSupportedEndpoints() {
        logger.fine("Fetching Agent info");

        HashSet<String> endpoints = new HashSet<>();

        HttpURLConnection conn = null;
        try {
            logger.fine("Setting up HttpURLConnection...");
            final URL traceAgentUrl = buildHttpURL(this.hostname, this.traceCollectionPort, "/info");
            conn = getHttpURLConnection(traceAgentUrl, HTTP_TIMEOUT_INFO_MS);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setUseCaches(false);

            // Get response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();

            JSONObject jsonResponse = new JSONObject(result.toString());
            JSONArray jsonEndpoints = jsonResponse.getJSONArray("endpoints");

            // Iterate jsonArray using for loop
            for (int i = 0; i < jsonEndpoints.length(); i++) {
                endpoints.add(jsonEndpoints.getString(i));
            }
        } catch (Exception e) {
            try {
                if (conn != null && conn.getResponseCode() == NOT_FOUND) {
                    logger.info("Agent /info returned 404. Requires Agent v6.27+ or v7.27+.");
                } else {
                    DatadogUtilities.severe(logger, e, "Unknown client error, please check your config");
                }
            } catch (IOException ex) {
                DatadogUtilities.severe(logger, ex, "Failed to inspect HTTP response");
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return endpoints;
    }

    /**
     * Posts a given payload to the Agent EVP Proxy so it is forwarded to the Webhook Intake.
     *
     * @param payload - A webhooks payload.
     * @return a boolean to signify the success or failure of the HTTP POST request.
     */
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    @Override
    public boolean postWebhook(String payload) {
        logger.fine("Sending webhook");

        if(!evpProxySupported){
            logger.severe("Trying to send a webhook but the Agent doesn't support it.");
            return false;
        }

        HttpURLConnection conn = null;
        try {
            logger.fine("Setting up HttpURLConnection...");
            String urlParameters = "?service=" + DatadogUtilities.getDatadogGlobalDescriptor().getCiInstanceName();
            final URL traceAgentUrl = buildHttpURL(this.hostname, this.traceCollectionPort, "/evp_proxy/v1/api/v2/webhook/"+urlParameters);
            conn = getHttpURLConnection(traceAgentUrl, HTTP_TIMEOUT_EVP_PROXY_MS);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Datadog-EVP-Subdomain", "webhook-intake");
            conn.setRequestProperty("DD-CI-PROVIDER-NAME", "jenkins");
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
                logger.fine(String.format("Webhook API call through Agent proxy was sent successfully!"));
                logger.fine(String.format("Payload: %s", payload));
            } else {
                logger.severe(String.format("Webhook API call through Agent proxy failed!"));
                logger.fine(String.format("Payload: %s", payload));
                logger.fine(String.format("Response: %s", result));
                return false;
            }
        } catch (Exception e) {
            try {
                if (conn != null && conn.getResponseCode() == BAD_REQUEST) {
                    logger.severe("We received a 400 from the Agent EVP Proxy.");
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
     * reinitialize the Tracer Client
     * @param force - force to reinitialize
     * @return true if reinitialized properly otherwise false
     */
    protected boolean reinitializeAgentHttpClient(boolean force) {
        if(!this.isStoppedAgentHttpClient && this.traceBuildLogic != null && this.tracePipelineLogic != null && !force) {
            return true;
        }

        if(!DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility() || this.getHostname() == null || this.getTraceCollectionPort() == null) {
            return false;
        }

        this.stopAgentHttpClient();
        try {
            logger.info("Re/Initialize Datadog-Plugin Agent Http Client");

            // Build
            final URL tracesURL = buildHttpURL(this.getHostname(), this.getTraceCollectionPort(), "/v0.3/traces");
            this.agentHttpClient = NonBlockingHttpClient.builder()
                    .errorHandler(LOGGER_HTTP_ERROR_HANDLER)
                    .messageRoute(PayloadMessage.Type.TRACE, HttpMessageFactory.builder()
                            .agentURL(tracesURL)
                            .httpMethod(HttpMessage.HttpMethod.PUT)
                            .payloadMapper(new JsonTraceSpanMapper())
                            .build())
                    .build();

            this.isStoppedAgentHttpClient = false;
            return true;
        } catch (Throwable e){
            DatadogUtilities.severe(logger, e, "Failed to reinitialize Datadog-Plugin Agent Http Client");
            this.stopAgentHttpClient();
            return false;
        }
    }

    protected boolean checkEvpProxySupportAndUpdateLogic() {
        if (evpProxySupported) {
            return true; // Once we have seen an Agent that supports EVP Proxy, we never check again.
        }
        if (System.currentTimeMillis() < (lastEvpProxyCheckTimeMs + EVP_PROXY_SUPPORT_TIME_BETWEEN_CHECKS_MS)) {
            return evpProxySupported; // Wait at least 1 hour between checks, return the cached value
        }
        synchronized (DatadogAgentClient.class) {
            if (!evpProxySupported) {
                Set<String> supportedAgentEndpoints = fetchAgentSupportedEndpoints();
                evpProxySupported = supportedAgentEndpoints.contains("/evp_proxy/v3/");
                lastEvpProxyCheckTimeMs = System.currentTimeMillis();
                if (evpProxySupported) {
                    logger.fine("EVP Proxy is supported by the Agent.");
                    traceBuildLogic = new DatadogWebhookBuildLogic(this);
                    tracePipelineLogic = new DatadogWebhookPipelineLogic(this);
                } else {
                    logger.info("The Agent doesn't support EVP Proxy, falling back to APM for CI Visibility. Requires Agent v6.42+ or 7.42+.");
                    traceBuildLogic = new DatadogTraceBuildLogic(this.agentHttpClient);
                    tracePipelineLogic = new DatadogTracePipelineLogic(this.agentHttpClient);
                }
            }
        }
        return evpProxySupported;
    }

    private boolean stopAgentHttpClient() {
        if(agentHttpClient != null) {
            try {
                this.agentHttpClient.stop();
            } catch (Throwable e) {
                DatadogUtilities.severe(logger, e, "Failed to stop Agent Http Client");
                return false;
            }
            this.agentHttpClient = null;
        }
        this.isStoppedAgentHttpClient = true;
        return true;
    }

    private boolean stopStatsDClient(){
        if (this.statsd != null){
            try{
                this.statsd.stop();
            }catch(Exception e){
                DatadogUtilities.severe(logger, e, "Failed to stop DogStatsD Client");
                return false;
            }
            this.statsd = null;
        }

        this.isStoppedStatsDClient = true;
        return true;
    }

    public String getHostname() {
        return hostname;
    }

    @Override
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Integer getPort() {
        return port;
    }

    @Override
    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getLogCollectionPort() {
        return logCollectionPort;
    }

    @Override
    public void setLogCollectionPort(Integer logCollectionPort) {
        this.logCollectionPort = logCollectionPort;
    }

    public Integer getTraceCollectionPort() {
        return traceCollectionPort;
    }

    @Override
    public void setUrl(String url) {
        // noop
    }

    @Override
    public void setLogIntakeUrl(String logIntakeUrl) {
        // noop
    }

    @Override
    public void setWebhookIntakeUrl(String webhookIntakeUrl) {
        // noop
    }

    @Override
    public void setApiKey(Secret apiKey){
        // noop
    }

    @Override
    public boolean isDefaultIntakeConnectionBroken() {
        return false;
    }

    @Override
    public void setDefaultIntakeConnectionBroken(boolean defaultIntakeConnectionBroken) {
        // noop
    }

    @Override
    public boolean isLogIntakeConnectionBroken() {
        return false;
    }

    @Override
    public void setLogIntakeConnectionBroken(boolean logIntakeConnectionBroken) {
        // noop
    }

    @Override
    public boolean isWebhookIntakeConnectionBroken() {
        return false;
    }

    @Override
    public void setWebhookIntakeConnectionBroken(boolean webhookIntakeConnectionBroken) {
        // noop
    }

    public boolean isEvpProxySupported() {
        return evpProxySupported;
    }

    @Override
    public boolean event(DatadogEvent event) {
        try {
            boolean status = reinitializeStatsDClient(false);
            if(!status){
                return false;
            }
            logger.fine("Sending event");
            Event ev = Event.builder()
                    .withTitle(event.getTitle())
                    .withText(event.getText())
                    .withPriority(event.getPriority().toEventPriority())
                    .withHostname(event.getHost())
                    .withAlertType(event.getAlertType().toEventAlertType())
                    .withAggregationKey(event.getAggregationKey())
                    .withSourceTypeName("jenkins")
                    .build();
            this.statsd.recordEvent(ev, TagsUtil.convertTagsToArray(event.getTags()));
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, "Failed to send event payload to DogStatsD");
            reinitializeStatsDClient(true);
            return false;
        }
    }

    @Override
    public boolean incrementCounter(String name, String hostname, Map<String, Set<String>> tags) {
        try {
            boolean status = reinitializeStatsDClient(false);
            if(!status){
                return false;
            }
            logger.fine("increment counter with dogStatD client");
            this.statsd.incrementCounter(name, TagsUtil.convertTagsToArray(tags));
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, "Failed to increment counter with DogStatsD");
            reinitializeStatsDClient(true);
            return false;
        }
    }

    @Override
    public void flushCounters() {
        return; //noop
    }

    @Override
    public boolean gauge(String name, long value, String hostname, Map<String, Set<String>> tags) {
        try {
            boolean status = reinitializeStatsDClient(false);
            if(!status){
                return false;
            }
            logger.fine("Submit gauge with dogStatD client");
            this.statsd.gauge(name, value, TagsUtil.convertTagsToArray(tags));
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, "Failed to send gauge metric payload to DogStatsD");
            reinitializeStatsDClient(true);
            return false;
        }
    }

    @Override
    public boolean serviceCheck(String name, Status status, String hostname, Map<String, Set<String>> tags) {
        try {
            boolean initStatus = reinitializeStatsDClient(false);
            if(!initStatus){
                return false;
            }
            logger.fine(String.format("Sending service check '%s' with status %s", name, status));

            ServiceCheck sc = ServiceCheck.builder()
                    .withName(name)
                    .withStatus(status.toServiceCheckStatus())
                    .withHostname(hostname)
                    .withTags(TagsUtil.convertTagsToArray(tags)).build();
            this.statsd.serviceCheck(sc);
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, "Failed to send service check to DogStatsD");
            reinitializeStatsDClient(true);
            return false;
        }
    }

    @Override
    public boolean sendLogs(String payload) {
        if(logCollectionPort == null){
            logger.severe("Datadog Log Collection Port is not set properly");
            return false;
        }

        if(this.ddLogger == null) {
            boolean status = reinitializeLogger(true);
            if(!status) {
                logger.info("Datadog Plugin Logger could not be initialized");
                return false;
            }
        }
        // Check if we have handlers in our logger. This may happen when ddLogger initialization fails
        // ddLogger may not be null but may be mis-configured.
        // Reset to null to reinitialize if needed.
        Handler[] handlers = this.ddLogger.getHandlers();
        if(handlers == null || handlers.length == 0){
            this.ddLogger = null;
            logger.info("Datadog Plugin Logger does not have handlers");
            return false;
        }

        try {

            final boolean retryLogs = DatadogUtilities.getDatadogGlobalDescriptor().isRetryLogs();

            this.ddLogger.info(payload);

            // We check for errors in our custom errorManager
            Handler handler = this.ddLogger.getHandlers()[0];
            DatadogErrorManager errorManager = (DatadogErrorManager)handler.getErrorManager();
            if(errorManager.hadReportedIssue()){
                reinitializeLogger(true);
                // NOTE: After a socket timeout, the first message to be sent get lost, it is only the second message
                // that gets reported as an error in the errorManager.
                // For this reason, we always keep the previousPayload in order to resubmit it.
                if (retryLogs) {
                    this.ddLogger.info(previousPayload);
                }
                previousPayload = payload;

                // we return false so that we retry to send the current payload message that still didn't get submitted.
                return false;
            }
            previousPayload = payload;
        }catch(Exception e){
            DatadogUtilities.severe(logger, e, "Failed to send log payload to DogStatsD");
            reinitializeStatsDClient(true);
            previousPayload = payload;
            return false;
        }
        return true;
    }

    @Override
    public boolean startBuildTrace(BuildData buildData, Run<?, ?> run) {
        try {
            boolean status = reinitializeAgentHttpClient(false);
            if(!status) {
                return false;
            }

            checkEvpProxySupportAndUpdateLogic();

            logger.fine("Started build trace");
            this.traceBuildLogic.startBuildTrace(buildData, run);
            return true;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to start build trace");
            reinitializeAgentHttpClient(true);
            return false;
        }
    }


    @Override
    public boolean finishBuildTrace(BuildData buildData, Run<?, ?> run) {
        try {
            boolean status = reinitializeAgentHttpClient(false);
            if(!status) {
                return false;
            }

            checkEvpProxySupportAndUpdateLogic();

            logger.fine("Finished build trace");
            this.traceBuildLogic.finishBuildTrace(buildData, run);
            return true;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to finish build trace");
            reinitializeAgentHttpClient(true);
            return false;
        }
    }

    @Override
    public boolean sendPipelineTrace(Run<?, ?> run, FlowNode flowNode) {
        try {
            boolean status = reinitializeAgentHttpClient(false);
            if(!status) {
                return false;
            }

            checkEvpProxySupportAndUpdateLogic();

            logger.fine("Send pipeline traces.");
            this.tracePipelineLogic.execute(run, flowNode);
            return true;
        } catch (Exception e){
            DatadogUtilities.severe(logger, e, "Failed to send pipeline trace");
            reinitializeAgentHttpClient(true);
            return false;
        }
    }

    public static class ConnectivityResult {
        private final boolean error;
        private final String errorMessage;

        public static final ConnectivityResult SUCCESS = new ConnectivityResult(false, null);

        public ConnectivityResult(final boolean error, final String errorMessage) {
            this.error = error;
            this.errorMessage = errorMessage;
        }

        public boolean isError() {
            return error;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }


}
