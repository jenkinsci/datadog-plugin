package org.datadog.jenkins.plugins.datadog.traces;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the ID generation logic for the TraceSpan
 */
public class IdGenerator {

    public static long generate(){
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
