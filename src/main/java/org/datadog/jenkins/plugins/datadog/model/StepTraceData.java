package org.datadog.jenkins.plugins.datadog.model;

import java.io.Serializable;

public class StepTraceData implements Serializable {

    private final long spanId;

    public StepTraceData(final long spanId) {
        this.spanId = spanId;
    }

    public long getSpanId() {
        return spanId;
    }
}
