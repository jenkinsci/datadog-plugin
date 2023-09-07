package org.datadog.jenkins.plugins.datadog.clients;

import java.util.Map;
import java.util.Set;

public interface Metrics extends AutoCloseable {
    /**
     * Sends a metric to the Datadog API, including the gauge name, and value.
     *
     * @param name     - A String with the name of the metric to record.
     * @param value    - A long containing the value to submit.
     * @param hostname - A String with the hostname to submit.
     * @param tags     - A Map containing the tags to submit.
     */
    void gauge(String name, long value, String hostname, Map<String, Set<String>> tags);
}
