package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;

public interface AgentHttpClient {

    void send(TraceSpan span);
}
