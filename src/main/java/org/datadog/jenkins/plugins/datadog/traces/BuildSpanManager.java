package org.datadog.jenkins.plugins.datadog.traces;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to propagate the build Span between onStart() and onComplete() methods.
 * This mechanism is needed because the Span object cannot be serialized in a Jenkins Action.
 */
public class BuildSpanManager {

    private static final BuildSpanManager INSTANCE = new BuildSpanManager();
    private final Map<String, TraceSpan> traceSpanByBuildTag = new HashMap<>();

    public static BuildSpanManager get() {
        return INSTANCE;
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
