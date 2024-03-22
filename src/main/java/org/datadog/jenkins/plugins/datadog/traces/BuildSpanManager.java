package org.datadog.jenkins.plugins.datadog.traces;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;

/**
 * Used to store trace data after the build has finished.
 * The data is needed to link upstream build to a downstream build.
 */
public class BuildSpanManager {

    private static final Logger LOGGER = Logger.getLogger(BuildSpanManager.class.getName());

    private static final BuildSpanManager INSTANCE = new BuildSpanManager();
    private final Map<String, TraceSpan.TraceSpanContext> contextByTag = new ConcurrentHashMap<>();
    private final BlockingQueue<String> tags = new ArrayBlockingQueue<>(getCapacity());

    public static BuildSpanManager get() {
        return INSTANCE;
    }

    public void put(final String tag, final TraceSpan.TraceSpanContext context) {
        while (!tags.offer(tag)) {
            // drop the oldest tag if the storage is full
            contextByTag.remove(tags.poll());
        }
        contextByTag.put(tag, context);
    }

    public TraceSpan.TraceSpanContext get(final String tag) {
        return contextByTag.get(tag);
    }

    private static int getCapacity() {
        String maxSize = System.getenv("DD_JENKINS_SPAN_CONTEXT_STORAGE_MAX_SIZE");
        if (maxSize != null) {
            try {
                return Integer.parseInt(maxSize);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid value for DD_JENKINS_SPAN_CONTEXT_STORAGE_MAX_SIZE: " + maxSize);
            }
        }
        return 1024;
    }

}
