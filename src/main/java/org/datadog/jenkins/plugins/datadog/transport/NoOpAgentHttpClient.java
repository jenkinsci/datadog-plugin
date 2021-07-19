package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;

public class NoOpAgentHttpClient implements AgentHttpClient{

    @Override
    public void send(TraceSpan span) {
        //N/A
    }
}
