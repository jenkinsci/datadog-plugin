package org.datadog.jenkins.plugins.datadog.transport;

public interface HttpErrorHandler {

    void handle(Exception exception);
}
