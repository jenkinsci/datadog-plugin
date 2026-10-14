package org.datadog.jenkins.plugins.datadog.traces.write;

import static org.junit.Assert.assertNull;

import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.junit.Test;

public class TraceWriteStrategyImplTest {

    @Test
    public void emptyBuildDataIsNotSerialized() {
        BuildData empty = BuildData.create(null, null);
        for (Track track : Track.values()) {
            assertNull(new TraceWriteStrategyImpl(track, payloads -> {}).serialize(empty, null));
        }
    }
}
