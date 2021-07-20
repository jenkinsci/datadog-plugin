package org.datadog.jenkins.plugins.datadog.clients.agent;

public interface AgentHttpMessageFactory<T> {

    HttpMessage create(T obj);
}
