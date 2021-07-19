package org.datadog.jenkins.plugins.datadog.traces;

import io.opentracing.Span;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to propagate the build Span between onStart() and onComplete() methods.
 * This mechanism is needed because the Span object cannot be serialized in a Jenkins Action.
 */
public class BuildSpanManager {

    private static final BuildSpanManager INSTANCE = new BuildSpanManager();
    //TODO Remove Java Tracer
    private final Map<String, Span> spanByBuildTag = new HashMap<>();

    private final Map<String, TraceSpan> traceSpanByBuildTag = new HashMap<>();

    public static BuildSpanManager get() {
        return INSTANCE;
    }

    //TODO Remove Java Tracer
    public Span putOld(final String tag, final Span span) {
        return spanByBuildTag.put(tag, span);
    }

    //TODO Remove Java Tracer
    public Span getOld(final String tag) {
        return spanByBuildTag.get(tag);
    }

    //TODO Remove Java Tracer
    public Span removeOld(final String tag){
        return spanByBuildTag.remove(tag);
    }


    public TraceSpan put(final String tag, final TraceSpan span) {
        return traceSpanByBuildTag.put(tag, span);
    }

    public TraceSpan get(final String tag) {
        return traceSpanByBuildTag.get(tag);
    }

    public TraceSpan remove(final String tag){
        return traceSpanByBuildTag.remove(tag);
    }


}
