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

import com.timgroup.statsd.Event;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.ServiceCheck;
import com.timgroup.statsd.StatsDClient;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SocketHandler;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper;
import org.datadog.jenkins.plugins.datadog.traces.write.AgentTraceWriteStrategy;
import org.datadog.jenkins.plugins.datadog.traces.write.Payload;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategy;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategyImpl;
import org.datadog.jenkins.plugins.datadog.traces.write.Track;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * This class is used to collect all methods that has to do with transmitting
 * data to Datadog.
 */
public class DatadogAgentClient implements DatadogClient {

    private static final int PAYLOAD_SIZE_LIMIT = 5 * 1024 * 1024; // 5 MB

    private static volatile DatadogAgentClient instance = null;
    // Used to determine if the instance failed last validation last time, so
    // we do not keep retrying to create the instance and logging the same error
    private static volatile boolean failedLastValidation = false;

    private static final Logger logger = Logger.getLogger(DatadogAgentClient.class.getName());

    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL")
    public static boolean enableValidations = true;

    private StatsDClient statsd;
    private Logger ddLogger;
    private String previousPayload;

    private final String hostname;
    private final Integer port;
    private final Integer logCollectionPort;
    private final Integer traceCollectionPort;
    private String resolvedIp = "";
    private boolean isStoppedStatsDClient = true;

    private final HttpClient client;


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
        }
        return instance;
    }

    protected DatadogAgentClient(String hostname, Integer port, Integer logCollectionPort, Integer traceCollectionPort) {
        this(hostname, port, logCollectionPort, traceCollectionPort, HTTP_TIMEOUT_EVP_PROXY_MS);
    }

    protected DatadogAgentClient(String hostname, Integer port, Integer logCollectionPort, Integer traceCollectionPort, long evpProxyTimeoutMillis) {
        this.hostname = hostname;
        this.port = port;
        this.logCollectionPort = logCollectionPort;
        this.traceCollectionPort = traceCollectionPort;
        this.client = new HttpClient(evpProxyTimeoutMillis);
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
            // need to close existing logger since it has a socket opened - not closing it leads to a file descriptor leak
            close(this.ddLogger);
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


    private void close(Logger logger) {
        if (logger == null) {
            return;
        }
        Handler[] handlers = logger.getHandlers();
        if (handlers == null) {
            return;
        }
        for (Handler handler : handlers) {
            try {
                handler.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * Fetches the supported endpoints from the Trace Agent /info API
     *
     * @return a list of endpoints (if /info wasn't available, it will be empty)
     */
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    public Set<String> fetchAgentSupportedEndpoints() {
        logger.fine("Fetching Agent info");

        String url = String.format("http://%s:%d/info", hostname, traceCollectionPort);
        try {
            return client.get(url, Collections.emptyMap(), s -> {
                JSONObject jsonResponse = new JSONObject(s);
                JSONArray jsonEndpoints = jsonResponse.getJSONArray("endpoints");

                Set<String> endpoints = new HashSet<>();
                for (int i = 0; i < jsonEndpoints.length(); i++) {
                    endpoints.add(jsonEndpoints.getString(i));
                }
                return endpoints;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Could not get the list of agent endpoints");
            return Collections.emptySet();
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Could not get the list of agent endpoints");
            return Collections.emptySet();
        }
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

    public Integer getPort() {
        return port;
    }

    public Integer getLogCollectionPort() {
        return logCollectionPort;
    }

    public Integer getTraceCollectionPort() {
        return traceCollectionPort;
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
    public Metrics metrics() {
        return new AgentMetrics();
    }

    private final class AgentMetrics implements Metrics {
        @Override
        public void gauge(String name, long value, String hostname, Map<String, Set<String>> tags) {
            try {
                boolean status = reinitializeStatsDClient(false);
                if (!status) {
                    return;
                }
                logger.fine("Submit gauge with dogStatD client");
                statsd.gauge(name, value, TagsUtil.convertTagsToArray(tags));
            } catch(Exception e){
                DatadogUtilities.severe(logger, e, "Failed to send gauge metric payload to DogStatsD");
                reinitializeStatsDClient(true);
            }
        }

        @Override
        public void close() throws Exception {
            // no op
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
    public TraceWriteStrategy createTraceWriteStrategy() {
        TraceWriteStrategyImpl evpStrategy = new TraceWriteStrategyImpl(Track.WEBHOOK, this::sendSpansToWebhook);
        TraceWriteStrategyImpl apmStrategy = new TraceWriteStrategyImpl(Track.APM, this::sendSpansToApm);
        return new AgentTraceWriteStrategy(evpStrategy, apmStrategy, this::isEvpProxySupported);
    }

    boolean isEvpProxySupported() {
        logger.info("Checking for EVP Proxy support in the Agent.");
        Set<String> supportedAgentEndpoints = fetchAgentSupportedEndpoints();
        return supportedAgentEndpoints.contains("/evp_proxy/v3/");
    }

    /**
     * Posts a given payload to the Agent EVP Proxy, so it is forwarded to the Webhook Intake.
     */
    private void sendSpansToWebhook(Collection<Payload> spans) {
        DatadogGlobalConfiguration datadogGlobalDescriptor = DatadogUtilities.getDatadogGlobalDescriptor();
        String urlParameters = datadogGlobalDescriptor != null ? "?service=" + datadogGlobalDescriptor.getCiInstanceName() : "";
        String url = String.format("http://%s:%d/evp_proxy/v1/api/v2/webhook/%s", hostname, traceCollectionPort, urlParameters);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Datadog-EVP-Subdomain", "webhook-intake");
        headers.put("DD-CI-PROVIDER-NAME", "jenkins");

        for (Payload span : spans) {
            if (span.getTrack() != Track.WEBHOOK) {
                logger.severe("Expected webhook track, got " + span.getTrack() + ", dropping span");
                continue;
            }

            byte[] body = span.getJson().toString().getBytes(StandardCharsets.UTF_8);
            if (body.length > PAYLOAD_SIZE_LIMIT) {
                logger.severe("Dropping span because payload size (" + body.length + ") exceeds the allowed limit of " + PAYLOAD_SIZE_LIMIT);
                continue;
            }

            // webhook intake does not support batch requests
            logger.fine("Sending webhook");
            client.postAsynchronously(url, headers, "application/json", body);
        }
    }

    private void sendSpansToApm(Collection<Payload> spans) {
        try {
            Map<String, net.sf.json.JSONArray> tracesById = new HashMap<>();
            for (Payload span : spans) {
                if (span.getTrack() != Track.APM) {
                    logger.severe("Expected APM track, got " + span.getTrack() + ", dropping span");
                    continue;
                }
                tracesById.computeIfAbsent(span.getJson().getString(JsonTraceSpanMapper.TRACE_ID), k -> new net.sf.json.JSONArray()).add(span.getJson());
            }

            final JSONArray jsonTraces = new JSONArray();
            for(net.sf.json.JSONArray trace : tracesById.values()) {
                jsonTraces.put(trace);
            }
            byte[] payload = jsonTraces.toString().getBytes(StandardCharsets.UTF_8);

            String tracesUrl = String.format("http://%s:%d/v0.3/traces", hostname, traceCollectionPort);
            client.put(tracesUrl, Collections.emptyMap(), "application/json", payload, Function.identity());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending trace", e);

        } catch (Exception e) {
            throw new RuntimeException("Error while sending trace", e);
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
