package org.datadog.jenkins.plugins.datadog.traces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.junit.Test;

public class BuildSpanManagerTest {

    @Test
    public void testStorageHasLimitedCapacity() {
        BuildSpanManager manager = new BuildSpanManager(3);
        manager.put("tag1", new TraceSpan.TraceSpanContext(1, 1, 1));
        manager.put("tag2", new TraceSpan.TraceSpanContext(2, 2, 2));
        manager.put("tag3", new TraceSpan.TraceSpanContext(3, 3, 3));

        assertEquals(new TraceSpan.TraceSpanContext(1, 1, 1), manager.get("tag1"));
        assertEquals(new TraceSpan.TraceSpanContext(2, 2, 2), manager.get("tag2"));
        assertEquals(new TraceSpan.TraceSpanContext(3, 3, 3), manager.get("tag3"));

        // the storage is full, adding a new element should remove the oldest one
        manager.put("tag4", new TraceSpan.TraceSpanContext(4, 4, 4));

        assertNull(manager.get("tag1"));
        assertEquals(new TraceSpan.TraceSpanContext(2, 2, 2), manager.get("tag2"));
        assertEquals(new TraceSpan.TraceSpanContext(3, 3, 3), manager.get("tag3"));
        assertEquals(new TraceSpan.TraceSpanContext(4, 4, 4), manager.get("tag4"));
    }

}