package org.datadog.jenkins.plugins.datadog.traces.write;

import javax.annotation.Nonnull;
import net.sf.json.JSONObject;

public class Payload {

    private final JSONObject json;
    private final Track track;

    public Payload(@Nonnull JSONObject json, @Nonnull Track track) {
        this.json = json;
        this.track = track;
    }

    @Nonnull
    public JSONObject getJson() {
        return json;
    }

    @Nonnull
    public Track getTrack() {
        return track;
    }
}
