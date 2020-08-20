package org.datadog.jenkins.plugins.datadog.traces;

import io.opentracing.propagation.TextMap;

import java.util.Iterator;
import java.util.Map;

/**
 * TextMap implementation to inject/extract SpanContext into BuildSpanAction.
 * This class works to propagate the OpenTracing ids.
 */
public class BuildTextMapAdapter implements TextMap {
    private final Map<String, String> map;

    public BuildTextMapAdapter(final Map<String, String> map) {
        this.map = map;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return map.entrySet().iterator();
    }

    @Override
    public void put(String key, String value) {
        map.put(key, value);
    }
}
