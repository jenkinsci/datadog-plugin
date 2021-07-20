package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;

public interface TraceSpanMapper {

    byte[] map(TraceSpan source);

    String contentType();
}
