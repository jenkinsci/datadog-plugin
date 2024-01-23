package org.datadog.jenkins.plugins.datadog.model;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

public enum Status {
    UNKNOWN((byte) 0), SUCCESS((byte) 1), UNSTABLE((byte) 2), ERROR((byte) 3), SKIPPED((byte) 4), CANCELED((byte) 5);

    private final byte weight;

    Status(byte weight) {
        this.weight = weight;
    }

    public String toTag() {
        return toString().toLowerCase();
    }

    public static Status fromJenkinsResult(String status) {
        return valueOf(DatadogUtilities.statusFromResult(status).toUpperCase());
    }

    /**
     * Combines two statuses, returning the worst one
     * (based on {@link hudson.model.Result#combine(hudson.model.Result, hudson.model.Result)}).
     */
    public static Status combine(Status a, Status b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.weight > b.weight ? a : b;
    }
}
