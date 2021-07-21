package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import java.util.logging.Logger;

public class LoggerHttpErrorHandler implements HttpErrorHandler{

    public static final HttpErrorHandler LOGGER_HTTP_ERROR_HANDLER = new LoggerHttpErrorHandler();

    private static final Logger logger = Logger.getLogger(LoggerHttpErrorHandler.class.getName());

    @Override
    public void handle(Exception exception) {
        DatadogUtilities.severe(logger, exception, exception.getMessage());
    }
}
