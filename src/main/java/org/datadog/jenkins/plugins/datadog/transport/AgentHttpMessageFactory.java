package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.transport.message.HttpMessage;

public interface AgentHttpMessageFactory<T> {

    HttpMessage create(T obj);
}
