package org.datadog.jenkins.plugins.datadog.traces.write;

import javax.annotation.Nonnull;
import net.sf.json.JSONObject;

public class Span {

    private final JSONObject payload;
    private final Track track;

    public Span(@Nonnull JSONObject payload, @Nonnull Track track) {
        this.payload = payload;
        this.track = track;
    }

    @Nonnull
    public JSONObject getPayload() {
        return payload;
    }

    @Nonnull
    public Track getTrack() {
        return track;
    }
}
