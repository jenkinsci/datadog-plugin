package org.datadog.jenkins.plugins.datadog.traces;

import java.util.concurrent.ThreadLocalRandom;

public class IdGenerator {

    public static long generate(){
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
