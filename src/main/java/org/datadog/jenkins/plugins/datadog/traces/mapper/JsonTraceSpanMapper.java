package org.datadog.jenkins.plugins.datadog.traces.mapper;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.transport.PayloadMapper;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class JsonTraceSpanMapper implements PayloadMapper<TraceSpan> {

    @Override
    public byte[] map(final TraceSpan span) {
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

        return jsonTraces.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String contentType() {
        return "application/json";
    }
}
