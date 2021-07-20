package org.datadog.jenkins.plugins.datadog.clients.agent;

public interface AgentHttpErrorHandler {

    void handle(Exception exception);
}
