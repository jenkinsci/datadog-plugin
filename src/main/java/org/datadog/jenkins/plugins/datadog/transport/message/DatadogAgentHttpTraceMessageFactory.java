package org.datadog.jenkins.plugins.datadog.transport.message;

import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;
import org.datadog.jenkins.plugins.datadog.transport.AgentHttpMessageFactory;
import org.datadog.jenkins.plugins.datadog.transport.TraceSpanMapper;

import java.net.URL;

public class DatadogAgentHttpTraceMessageFactory implements AgentHttpMessageFactory<TraceSpan> {

    private final URL tracesURL;
    private final TraceSpanMapper mapper;

    public DatadogAgentHttpTraceMessageFactory(final URL tracesURL, final TraceSpanMapper mapper) {
        this.tracesURL = tracesURL;
        this.mapper = mapper;
    }

    @Override
    public HttpMessage create(final TraceSpan span) {
        return new HttpMessage(tracesURL, "PUT", this.mapper.contentType(), this.mapper.map(span));
    }
}
