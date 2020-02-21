package org.datadog.jenkins.plugins.datadog.clients;

import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class DatadogFormatter extends SimpleFormatter {
    private static final String format = "%1$s %n";

    @Override
    public synchronized String format(LogRecord lr) {
        return String.format(format, lr.getMessage());
    }

}
