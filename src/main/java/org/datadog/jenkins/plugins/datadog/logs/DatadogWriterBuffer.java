package org.datadog.jenkins.plugins.datadog.logs;

import net.sf.json.JSONObject;

import java.util.Queue;

public class DatadogWriterBuffer {
    Queue<JSONObject> queue;

    public DatadogWriterBuffer(Queue<JSONObject> queue) {
        this.queue = queue;
    }

    public JSONObject getPayload() {
        // retrieve from queue;
        if (!queue.isEmpty()) {
            return queue.poll();
        }

        return null;
    }

    public void put(JSONObject payload) {
        queue.offer(payload);
    }
}
