package org.datadog.jenkins.plugins.datadog.traces;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;

/**
 * Used to propagate the build Span between onStart() and onComplete() methods.
 * This mechanism is needed because the Span object cannot be serialized in a Jenkins Action.
 */
public class BuildSpanManager {

    private static final Logger LOGGER = Logger.getLogger(BuildSpanManager.class.getName());

    private static final BuildSpanManager INSTANCE = new BuildSpanManager();
    private final Map<String, TraceSpan.TraceSpanContext> inProgress = new ConcurrentHashMap<>();

    /**
     * The last N finished contexts are stored to be used by the logic that links upstream pipelines to downstream pipelines
     */
    private final Map<String, TraceSpan.TraceSpanContext> finished = new ConcurrentHashMap<>();
    private final BlockingQueue<String> finishedTags = new ArrayBlockingQueue<>(getFinishedContextsCapacity());

    public static BuildSpanManager get() {
        return INSTANCE;
    }

    public void put(final String tag, final TraceSpan.TraceSpanContext context) {
        inProgress.put(tag, context);
    }

    public TraceSpan.TraceSpanContext get(final String tag) {
        TraceSpan.TraceSpanContext inProgressContext = inProgress.get(tag);
        if (inProgressContext != null) {
            return inProgressContext;
        } else {
            return finished.get(tag);
        }
    }

    public void remove(final String tag){
        TraceSpan.TraceSpanContext context = inProgress.remove(tag);
        if (context == null) {
            return;
        }

        finished.put(tag, context);

        while (!finishedTags.offer(tag)) {
            finished.remove(finishedTags.poll());
        }
    }

    private static int getFinishedContextsCapacity() {
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
