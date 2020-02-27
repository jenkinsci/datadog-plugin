package org.datadog.jenkins.plugins.datadog.trace;

import io.opentracing.Span;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;

import java.util.HashMap;
import java.util.Map;

public final class DatadogTraceCache {
    // TODO: Make this a expiring cache with proper settings
    // TODO: make it thread safe using a ConcurrentMap
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static Map<String, AugmentedSpan> cache = new HashMap<>();

    public static class AugmentedSpan {
        public Span span;
        public String traceId;
        public String spanId;

        public AugmentedSpan(Span span, String traceId, String spanId){
            this.span = span;
            this.traceId = traceId;
            this.spanId = spanId;
        }
    }
}
