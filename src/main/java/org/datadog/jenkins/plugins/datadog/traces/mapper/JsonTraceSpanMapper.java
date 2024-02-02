package org.datadog.jenkins.plugins.datadog.traces.mapper;

import java.util.Map;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;

/**
 * PayloadMapper to transform TraceSpan into JSON object
 * following the spec: https://docs.datadoghq.com/api/latest/tracing/
 */
public class JsonTraceSpanMapper {

    public static final String TRACE_ID = "trace_id";
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

    public JSONObject map(final TraceSpan span) {
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

        return jsonSpan;
    }
}
