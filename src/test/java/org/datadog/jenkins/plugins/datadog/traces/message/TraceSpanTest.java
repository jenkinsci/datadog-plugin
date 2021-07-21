package org.datadog.jenkins.plugins.datadog.traces.message;

import static org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan.PRIORITY_SAMPLING_KEY;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TraceSpanTest {

    @Test
    public void testCorrectPrioritySamplingValue() {
        final TraceSpan sut = new TraceSpan("test-name", 0);
        assertEquals(Double.valueOf(1), sut.getMetrics().get(PRIORITY_SAMPLING_KEY));
    }
}
