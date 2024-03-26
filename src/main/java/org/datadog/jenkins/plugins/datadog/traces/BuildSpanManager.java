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

    public static final String DD_JENKINS_SPAN_CONTEXT_STORAGE_MAX_SIZE_ENV = "DD_JENKINS_SPAN_CONTEXT_STORAGE_MAX_SIZE";
    private static final int DEFAULT_CONTEXT_STORAGE_MAX_SIZE = 1024;

    private static final BuildSpanManager INSTANCE = new BuildSpanManager(getCapacity());

    private final Map<String, TraceSpan.TraceSpanContext> contextByTag;
    private final BlockingQueue<String> tags;

    public static BuildSpanManager get() {
        return INSTANCE;
    }

    BuildSpanManager(int capacity) {
        this.tags = new ArrayBlockingQueue<>(capacity);
        this.contextByTag = new ConcurrentHashMap<>();
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
        String maxSize = System.getenv(DD_JENKINS_SPAN_CONTEXT_STORAGE_MAX_SIZE_ENV);
        if (maxSize != null) {
            try {
                int parsedMaxSize = Integer.parseInt(maxSize);
                if (parsedMaxSize > 0) {
                    return parsedMaxSize;
                } else {
                    LOGGER.warning("Invalid value for " + DD_JENKINS_SPAN_CONTEXT_STORAGE_MAX_SIZE_ENV + ": " + parsedMaxSize);
                }
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid value for " + DD_JENKINS_SPAN_CONTEXT_STORAGE_MAX_SIZE_ENV + ": " + maxSize);
            }
        }
        return DEFAULT_CONTEXT_STORAGE_MAX_SIZE;
    }

}
