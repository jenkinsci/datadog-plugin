package org.datadog.jenkins.plugins.datadog.clients.agent;

public interface AgentHttpClient {

    void send(AgentMessage obj);

    void stop();

    void close();
}
