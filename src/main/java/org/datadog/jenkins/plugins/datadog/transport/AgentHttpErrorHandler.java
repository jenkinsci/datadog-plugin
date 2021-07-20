package org.datadog.jenkins.plugins.datadog.transport;

public interface AgentHttpErrorHandler {

    void handle(Exception exception);
}
