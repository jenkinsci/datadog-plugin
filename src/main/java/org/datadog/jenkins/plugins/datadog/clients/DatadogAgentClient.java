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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SocketHandler;
import javax.annotation.concurrent.GuardedBy;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.metrics.MetricsClient;
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

    private static final Logger logger = Logger.getLogger(DatadogAgentClient.class.getName());

    private final String hostname;
    private final Integer port;
    private final Integer logCollectionPort;
    private final Integer traceCollectionPort;

    private final HttpClient client;

    // logs
    private volatile Logger ddLogger;
    private final Object ddLoggerInitLock = new Object();
    private String previousPayload;

    // statsd
    private volatile StatsDClient statsd;
    private final Object statsdInitLock = new Object();
    @GuardedBy("statsdInitLock")
    private String resolvedIp;

    /**
     * Timeout of 1 minute for connecting and reading via the synchronous Agent EVP Proxy.
     * this prevents this plugin from causing jobs to hang in case of
     * flaky network or Datadog being down. Left intentionally long.
     */
    private static final int HTTP_TIMEOUT_EVP_PROXY_MS = 60 * 1000;

    public DatadogAgentClient(String hostname, Integer port, Integer logCollectionPort, Integer traceCollectionPort) {
        this(hostname, port, logCollectionPort, traceCollectionPort, HTTP_TIMEOUT_EVP_PROXY_MS);
    }

    public DatadogAgentClient(String hostname, Integer port, Integer logCollectionPort, Integer traceCollectionPort, long evpProxyTimeoutMillis) {
        validate(hostname, port, logCollectionPort, traceCollectionPort);

        this.hostname = hostname;
        this.port = port;
        this.logCollectionPort = logCollectionPort;
        this.traceCollectionPort = traceCollectionPort;
        this.client = new HttpClient(evpProxyTimeoutMillis);
    }

    private static void validate(String hostname, Integer port, Integer logCollectionPort, Integer traceCollectionPort) {
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

    public static ConnectivityResult checkConnectivity(final String host, final int port) {
        try(Socket ignored = new Socket(host, port)) {
            return ConnectivityResult.SUCCESS;
        } catch (Exception ex) {
            DatadogUtilities.severe(logger, ex, "Failed to create socket to host: " + host + ", port: " +port + ". Error: " + ex);
            return new ConnectivityResult(true, ex.toString());
        }
    }

    /**
     * Fetches the supported endpoints from the Trace Agent /info API
     *
     * @return a set of endpoints (if /info wasn't available, it will be empty)
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
    public MetricsClient metrics() {
        return new AgentMetrics();
    }

    private final class AgentMetrics implements MetricsClient {
        @Override
        public void gauge(String name, double value, String hostname, Map<String, Set<String>> tags) {
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
        public void rate(String name, double value, String hostname, Map<String, Set<String>> tags) {
            try {
                boolean status = reinitializeStatsDClient(false);
                if(!status){
                    return;
                }
                logger.fine("increment counter with dogStatD client");
                statsd.count(name, value, TagsUtil.convertTagsToArray(tags));
            } catch(Exception e){
                DatadogUtilities.severe(logger, e, "Failed to increment counter with DogStatsD");
                reinitializeStatsDClient(true);
            }
        }

        @Override
        public void close() {
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

    /**
     * reinitialize the dogStatsD Client
     * @param force - force to reinitialize
     * @return true if reinitialized properly otherwise false
     */
    private boolean reinitializeStatsDClient(boolean force) {
        synchronized (statsdInitLock) {
            try {
                boolean refreshClient = DatadogUtilities.getDatadogGlobalDescriptor().isRefreshDogstatsdClient();
                if (this.statsd != null && !force && (!refreshClient || !this.hasIpChanged())) {
                    return true;
                }

                stopStatsDClient();
                logger.info("Re/Initialize DogStatsD Client: hostname = " + this.hostname + ", port = " + this.port);
                this.statsd = new NonBlockingStatsDClient(null, this.hostname, this.port);
                return true;

            } catch (Exception e){
                DatadogUtilities.severe(logger, e, "Failed to reinitialize DogStatsD Client");
                return false;
            }
        }
    }

    private boolean hasIpChanged() throws UnknownHostException {
        String ipAddress = this.resolveHostnameIp();
        if (Objects.equals(this.resolvedIp, ipAddress)) {
            return false;
        } else {
            this.resolvedIp = ipAddress;
            return true;
        }
    }

    private String resolveHostnameIp() throws UnknownHostException {
        InetAddress inet = InetAddress.getByName(this.hostname);
        return inet.getHostAddress();
    }

    private void stopStatsDClient() {
        if (this.statsd != null) {
            try {
                this.statsd.stop();
            } catch (Exception e) {
                DatadogUtilities.severe(logger, e, "Failed to stop DogStatsD Client");
            }
            this.statsd = null;
        }
    }

    @Override
    public boolean sendLogs(String payload) {
        if(logCollectionPort == null){
            logger.severe("Datadog Log Collection Port is not set properly");
            return false;
        }

        if(this.ddLogger == null) {
            boolean status = reinitializeLogger(false);
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
            this.ddLogger.info(payload);

            // We check for errors in our custom errorManager
            Handler handler = this.ddLogger.getHandlers()[0];
            DatadogErrorManager errorManager = (DatadogErrorManager)handler.getErrorManager();
            if(errorManager.hadReportedIssue()){
                reinitializeLogger(true);
                // NOTE: After a socket timeout, the first message to be sent get lost, it is only the second message
                // that gets reported as an error in the errorManager.
                // For this reason, we always keep the previousPayload in order to resubmit it.
                boolean retryLogs = DatadogUtilities.getDatadogGlobalDescriptor().isRetryLogs();
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
            previousPayload = payload;
            return false;
        }
        return true;
    }

    /**
     * reinitialize the Logger Client
     * @return true if reinitialized properly otherwise false
     */
    private synchronized boolean reinitializeLogger(boolean force) {
        synchronized (ddLoggerInitLock) {
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
    }

    private static void close(Logger logger) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DatadogAgentClient that = (DatadogAgentClient) o;
        return Objects.equals(hostname, that.hostname)
                && Objects.equals(port, that.port)
                && Objects.equals(logCollectionPort, that.logCollectionPort)
                && Objects.equals(traceCollectionPort, that.traceCollectionPort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, port, logCollectionPort, traceCollectionPort);
    }
}
