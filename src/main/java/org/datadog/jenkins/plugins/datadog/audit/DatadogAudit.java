package org.datadog.jenkins.plugins.datadog.audit;


import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class used to centralize some util methods to show telemetry metrics via Jenkins logs.
 * This logger can be opt-in by the user in the Jenkins logs manager.
 */
public class DatadogAudit {

    private static final Logger LOGGER = Logger.getLogger(DatadogAudit.class.getName());

    static {
        log("## DatadogAudit enabled ##");
    }

    public static void log(String msg) {
        if(!LOGGER.isLoggable(Level.FINE)){
            return;
        }

        LOGGER.fine(msg);
    }

    public static void log(String msg, long start, long end) {
        if(!LOGGER.isLoggable(Level.FINE)){
            return;
        }

        long duration = end - start;
        if(duration > 10){
            LOGGER.fine(msg +" [duration: "+duration+" ms, start: " + start + ", end: " + end+"]");
        }
    }
}
