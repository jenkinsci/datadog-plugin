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
    private final Map<String, Span> spanByBuildTag = new HashMap<>();

    public static BuildSpanManager get() {
        return INSTANCE;
    }

    public Span put(final String tag, final Span span) {
        return spanByBuildTag.put(tag, span);
    }

    public Span get(final String tag) {
        return spanByBuildTag.get(tag);
    }

    public Span remove(final String tag){
        return spanByBuildTag.remove(tag);
    }
}
