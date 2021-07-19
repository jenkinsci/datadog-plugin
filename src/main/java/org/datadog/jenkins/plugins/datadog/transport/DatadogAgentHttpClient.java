package org.datadog.jenkins.plugins.datadog.transport;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;

import java.util.Map;

public class DatadogAgentHttpClient {

    public void send(TraceSpan span) {
        final JSONObject jsonSpan = new JSONObject();
        jsonSpan.put("trace_id", span.context().getTraceId());
        jsonSpan.put("span_id", span.context().getSpanId());
        if(span.context().getParentId() != 0){
            jsonSpan.put("parent_id", span.context().getParentId());
        }
        jsonSpan.put("name", span.getName());
        jsonSpan.put("resource", span.getResource());
        jsonSpan.put("service", span.getService());
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

        jsonSpan.put("start", span.getStartNs());
        jsonSpan.put("duration", span.getDurationNs());

        JSONArray jsonTrace = new JSONArray();
        jsonTrace.add(jsonSpan);

        JSONArray jsonTraces = new JSONArray();
        jsonTraces.add(jsonTrace);

        System.out.println(jsonTraces.toString());
    }
}
