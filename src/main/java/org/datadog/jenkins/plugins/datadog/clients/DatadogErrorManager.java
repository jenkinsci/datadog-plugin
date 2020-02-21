package org.datadog.jenkins.plugins.datadog.clients;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import java.util.logging.ErrorManager;
import java.util.logging.Logger;

public class DatadogErrorManager extends ErrorManager {

    private static final Logger logger = Logger.getLogger(DatadogErrorManager.class.getName());

    private boolean reported = false;

    public synchronized void error(String msg, Exception ex, int code) {
        if (reported) {
            // We only report the first error, to avoid clogging
            // the screen.
            return;
        }
        reported = true;
        String text = "java.util.logging.ErrorManager: " + code;
        if (msg != null) {
            text = text + ": " + msg;
        }
        DatadogUtilities.severe(logger, ex, text);

    }

    public boolean hadReportedIssue(){
        return reported;
    }

}
