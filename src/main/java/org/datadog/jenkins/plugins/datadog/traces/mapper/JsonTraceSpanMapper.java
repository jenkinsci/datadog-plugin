package org.datadog.jenkins.plugins.datadog.traces.mapper;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.transport.PayloadMapper;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PayloadMapper to transform TraceSpan into JSON object
 * following the spec: https://docs.datadoghq.com/api/latest/tracing/
 */
public class JsonTraceSpanMapper implements PayloadMapper<List<TraceSpan>> {

    static final String TRACE_ID = "trace_id";
    static final String SPAN_ID = "span_id";
    static final String PARENT_ID = "parent_id";

    static final String ERROR = "error";
    static final String OPERATION_NAME = "name";
    static final String RESOURCE_NAME = "resource";
    static final String SERVICE_NAME = "service";
    static final String SPAN_TYPE = "type";

    static final String META = "meta";
    static final String METRICS = "metrics";
    static final String START = "start";
    static final String DURATION = "duration";

    @Override
    public byte[] map(final List<TraceSpan> spans) {
        final Map<Long, JSONArray> tracesMap = new HashMap<>();
        for(final TraceSpan span : spans) {
            JSONArray jsonArray = tracesMap.get(span.context().getTraceId());
            if(jsonArray == null){
                jsonArray = new JSONArray();
                tracesMap.put(span.context().getTraceId(), jsonArray);
            }

            final JSONObject jsonSpan = new JSONObject();
            jsonSpan.put(TRACE_ID, span.context().getTraceId());
            jsonSpan.put(SPAN_ID, span.context().getSpanId());
            if(span.context().getParentId() != 0){
                jsonSpan.put(PARENT_ID, span.context().getParentId());
            }

            if(span.isError()){
                jsonSpan.put(ERROR, 1);
            }

            jsonSpan.put(OPERATION_NAME, span.getOperationName());
            jsonSpan.put(RESOURCE_NAME, span.getResourceName());
            jsonSpan.put(SERVICE_NAME, span.getServiceName());
            jsonSpan.put(SPAN_TYPE, span.getType());

            final JSONObject jsonMeta = new JSONObject();
            final Map<String, String> meta = span.getMeta();
            for(Map.Entry<String, String> metaEntry : meta.entrySet()) {
                jsonMeta.put(metaEntry.getKey(), metaEntry.getValue());
            }
            jsonSpan.put(META, jsonMeta);

            final JSONObject jsonMetrics = new JSONObject();
            final Map<String, Double> metrics = span.getMetrics();
            for(Map.Entry<String, Double> metric : metrics.entrySet()){
                jsonMetrics.put(metric.getKey(), metric.getValue());
            }
            jsonSpan.put(METRICS, jsonMetrics);

            jsonSpan.put(START, span.getStartNano());
            jsonSpan.put(DURATION, span.getDurationNano());

            jsonArray.add(jsonSpan);
        }

        final JSONArray jsonTraces = new JSONArray();
        for(Map.Entry<Long, JSONArray> traceEntry : tracesMap.entrySet()) {
            jsonTraces.add(traceEntry.getValue());
        }
        return jsonTraces.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String contentType() {
        return "application/json";
    }
}
