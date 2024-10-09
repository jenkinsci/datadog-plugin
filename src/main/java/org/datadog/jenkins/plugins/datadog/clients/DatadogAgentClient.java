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
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.logs.LogWriteStrategy;
import org.datadog.jenkins.plugins.datadog.metrics.MetricsClient;
import org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper;
import org.datadog.jenkins.plugins.datadog.traces.write.*;
import org.datadog.jenkins.plugins.datadog.util.CircuitBreaker;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.concurrent.GuardedBy;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

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
    public LogWriteStrategy createLogWriteStrategy() {
        if (logCollectionPort == null) {
            logger.severe("Datadog Log Collection Port is not set properly, logs will not be written to Datadog");
            return LogWriteStrategy.NO_OP;
        }
        return new AgentLogWriteStrategy(hostname, logCollectionPort);
    }

    private static final class AgentLogWriteStrategy implements LogWriteStrategy {
        private static final byte[] LINE_SEPARATOR = System.lineSeparator().getBytes(StandardCharsets.UTF_8);

        private final String host;
        private final int port;

        private final CircuitBreaker<List<String>> circuitBreaker;

        private Socket socket;
        private OutputStream out;

        private AgentLogWriteStrategy(String host, int port) {
            this.host = host;
            this.port = port;
            this.circuitBreaker = new CircuitBreaker<>(
                    this::doSend,
                    this::fallback,
                    this::handleError,
                    100,
                    CircuitBreaker.DEFAULT_MAX_HEALTH_CHECK_DELAY_MILLIS,
                    CircuitBreaker.DEFAULT_DELAY_FACTOR);
        }

        public void send(List<String> payloads) {
            circuitBreaker.accept(payloads);
        }

        private void doSend(List<String> payloads) throws Exception {
            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                socket = new Socket(host, port);
                out = new BufferedOutputStream(socket.getOutputStream());
            }
            for (String payload : payloads) {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.write(LINE_SEPARATOR);
            }
        }

        private void handleError(Exception e) {
            socket = null;
            DatadogUtilities.severe(logger, e, "Could not write logs to agent");
        }

        private void fallback(List<String> payloads) {
            // cannot establish connection to agent, do nothing
        }

        @Override
        public void close() {
            try {
                if (out != null) {
                    flushSafely();
                    out.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                DatadogUtilities.severe(logger, e, "Error when closing agent logs writer");
            }
        }

        private void flushSafely() throws IOException {
            try {
                out.flush();
            } catch (IOException e) {
                DatadogUtilities.severe(logger, e, "Error when flushing agent logs writer");
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
     * Fetches the supported endpoints from the Trace Agent /info API
     *
     * @return a set of endpoints (if /info wasn't available, it will be empty)
     */
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    Set<String> fetchAgentSupportedEndpoints() {
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
