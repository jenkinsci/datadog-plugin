package org.datadog.jenkins.plugins.datadog.traces.write;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;

public class Payload {

    private final JSONObject json;
    private final Track track;

    public Payload(@NonNull JSONObject json, @NonNull Track track) {
        this.json = json;
        this.track = track;
    }

    @NonNull
    public JSONObject getJson() {
        return json;
    }

    @NonNull
    public Track getTrack() {
        return track;
    }
}
