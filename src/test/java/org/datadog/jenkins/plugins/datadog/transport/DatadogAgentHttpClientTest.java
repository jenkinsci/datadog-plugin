package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;
import org.junit.Test;

public class DatadogAgentHttpClientTest {

    @Test
    public void testPrintJson() {
        //Given
        final DatadogAgentHttpClient sut = DatadogAgentHttpClient.builder().agentHost("localhost").traceAgentPort(9998).build();
        final TraceSpan span = new TraceSpan("jenkins.build", 1626692256000000000L);
        span.setType("ci");
        span.setResource("some-resource");
        span.setService("some-service");
        span.putMeta("some-key", "some-value");
        span.putMetric("some-key", 1.0);
        span.setEndNano(1626692263000000000L);

        //When
        sut.send(span);

        //Then
    }

}