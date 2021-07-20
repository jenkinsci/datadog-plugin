package org.datadog.jenkins.plugins.datadog.transport;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class DatadogAgentHttpClient implements AgentHttpClient {

    private final URL tracesURL;

    private DatadogAgentHttpClient(final Builder builder) {
        try {
            final URL baseURL = new URL("http://" + builder.agentHost + ":" + builder.traceAgentPort);
            this.tracesURL = new URL(baseURL, "/v0.3/traces");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static DatadogAgentHttpClient.Builder builder() {
        return new Builder();
    }

    public void send(TraceSpan span) {
        HttpURLConnection conn = null;
        boolean status = true;
        try {
            conn = (HttpURLConnection) tracesURL.openConnection();
            int timeoutMS = 1 * 60 * 1000;
            conn.setConnectTimeout(timeoutMS);
            conn.setReadTimeout(timeoutMS);
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            final JSONArray jsonTraces = createTraces(span);
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
            wr.write(jsonTraces.toString());
            wr.close();

            int httpStatus = conn.getResponseCode();
            System.out.println(""+httpStatus);

        } catch (Exception ex) {
            System.out.println("--- EXCEPTION: " + ex);
            try {
                if(conn != null) {
                    System.out.println("--- Failed HTTP Status: " + conn.getResponseCode());
                }
            } catch (IOException ioex) {
                System.out.println("--- Failed to inspect HTTP response");
            }
            status = false;
        } finally {
            if(conn != null) {
                conn.disconnect();
            }
        }
    }

    public JSONArray createTraces(final TraceSpan span) {
        final JSONObject jsonSpan = new JSONObject();
        jsonSpan.put("trace_id", span.context().getTraceId());
        jsonSpan.put("span_id", span.context().getSpanId());
        if(span.context().getParentId() != 0){
            jsonSpan.put("parent_id", span.context().getParentId());
        }

        if(span.isError()){
            jsonSpan.put("error", 1);
        }

        jsonSpan.put("name", span.getOperationName());
        jsonSpan.put("resource", span.getResourceName());
        jsonSpan.put("service", span.getServiceName());
        jsonSpan.put("type", span.getType());

        final JSONObject jsonMeta = new JSONObject();
        final Map<String, String> meta = span.getMeta();
        for(Map.Entry<String, String> metaEntry : meta.entrySet()) {
            jsonMeta.put(metaEntry.getKey(), metaEntry.getValue());
        }
        jsonSpan.put("meta", jsonMeta);

        final JSONObject jsonMetrics = new JSONObject();
        final Map<String, Double> metrics = span.getMetrics();
        for(Map.Entry<String, Double> metric : metrics.entrySet()){
            jsonMetrics.put(metric.getKey(), metric.getValue());
        }
        jsonSpan.put("metrics", jsonMetrics);

        jsonSpan.put("start", span.getStartNano());
        jsonSpan.put("duration", span.getDurationNano());

        JSONArray jsonTrace = new JSONArray();
        jsonTrace.add(jsonSpan);

        JSONArray jsonTraces = new JSONArray();
        jsonTraces.add(jsonTrace);

        System.out.println(jsonTraces);
        return jsonTraces;
    }

    public static class Builder {

        private String agentHost;
        private int traceAgentPort;

        public Builder agentHost(final String agentHost) {
            this.agentHost = agentHost;
            return this;
        }

        public Builder traceAgentPort(final int traceAgentPort) {
            this.traceAgentPort = traceAgentPort;
            return this;
        }

        public DatadogAgentHttpClient build() {
            return new DatadogAgentHttpClient(this);
        }

    }
}
