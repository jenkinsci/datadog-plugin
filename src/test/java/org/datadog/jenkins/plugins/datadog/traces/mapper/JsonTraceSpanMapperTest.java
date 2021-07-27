package org.datadog.jenkins.plugins.datadog.traces.mapper;

import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.DURATION;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.ERROR;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.META;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.METRICS;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.OPERATION_NAME;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.PARENT_ID;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.RESOURCE_NAME;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.SERVICE_NAME;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.SPAN_ID;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.SPAN_TYPE;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.START;
import static org.datadog.jenkins.plugins.datadog.traces.mapper.JsonTraceSpanMapper.TRACE_ID;
import static org.junit.Assert.*;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class JsonTraceSpanMapperTest {

    public static final JsonTraceSpanMapper sut = new JsonTraceSpanMapper();

    @Test
    public void testJsonTraceSpanMapper() {
        //Given
        final TraceSpan span = new TraceSpan("test-operation-name", 0, new TraceSpan.TraceSpanContext());
        span.setResourceName("test-resource-name");
        span.setServiceName("test-service-name");
        span.setType("test-type");
        span.setError(true);
        span.getMeta().put("meta-key", "meta-value");
        span.getMetrics().put("metric-key", 1.0);
        span.setEndNano(1000);

        //When
        final JSONArray traces = JSONArray.fromObject(new String(sut.map(Collections.singletonList(span)), StandardCharsets.UTF_8));

        //Then
        assertEquals(1, traces.size());
        JSONArray trace = (JSONArray) traces.get(0);
        assertEquals(1, trace.size());

        final JSONObject jsonSpan = (JSONObject) trace.get(0);
        assertNotEquals(0, jsonSpan.get(TRACE_ID));
        assertNotEquals(0, jsonSpan.get(SPAN_ID));
        assertNotEquals(0, jsonSpan.get(PARENT_ID));
        assertEquals(1, jsonSpan.get(ERROR));
        assertEquals("test-operation-name", jsonSpan.get(OPERATION_NAME));
        assertEquals("test-resource-name", jsonSpan.get(RESOURCE_NAME));
        assertEquals("test-service-name", jsonSpan.get(SERVICE_NAME));
        assertEquals("test-type", jsonSpan.get(SPAN_TYPE));
        assertEquals("meta-value", ((Map<String, String>)jsonSpan.get(META)).get("meta-key"));
        assertEquals(1, ((Map<String, String>)jsonSpan.get(METRICS)).get("metric-key"));

        assertEquals(0, jsonSpan.get(START));
        assertEquals(1000, jsonSpan.get(DURATION));
    }

    @Test
    public void testSameTrace() {
        //Given
        final TraceSpan rootSpan = new TraceSpan("root", 0);
        final TraceSpan childSpan = new TraceSpan("child", 0, rootSpan.context());

        //When
        final JSONArray traces = JSONArray.fromObject(new String(sut.map(Arrays.asList(rootSpan, childSpan)), StandardCharsets.UTF_8));

        //Then
        assertEquals(1, traces.size());
    }

    @Test
    public void testDifferentTrace() {
        //Given
        final TraceSpan rootOneSpan = new TraceSpan("rootOne", 0);
        final TraceSpan rootTwoSpan = new TraceSpan("rootTwo", 0);

        //When
        final JSONArray traces = JSONArray.fromObject(new String(sut.map(Arrays.asList(rootOneSpan, rootTwoSpan)), StandardCharsets.UTF_8));

        //Then
        assertEquals(2, traces.size());
    }
}