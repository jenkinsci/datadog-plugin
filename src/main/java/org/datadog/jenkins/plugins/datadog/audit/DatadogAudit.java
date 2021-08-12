package org.datadog.jenkins.plugins.datadog.audit;


import java.util.logging.Level;
import java.util.logging.Logger;

public class DatadogAudit {

    private static transient final Logger LOGGER = Logger.getLogger(DatadogAudit.class.getName());

    static {
        logFine("## DatadogAudit enabled ##");
    }

    private static void logFine(String msg) {
        if(!LOGGER.isLoggable(Level.FINE)){
            return;
        }

        LOGGER.fine(msg);
    }

    public static long currentTimeMillis() {
        if(!LOGGER.isLoggable(Level.FINE)){
            return -1L;
        }

        return System.currentTimeMillis();
    }

    public static void log(String msg, long start, long end) {
        if(start == -1L || end == -1L){
            return;
        }

        long duration = end - start;
        if(duration > 10){
            logFine(msg +" [duration: "+duration+" ms, start: " + start + ", end: " + end+"]");
        }
    }
}
