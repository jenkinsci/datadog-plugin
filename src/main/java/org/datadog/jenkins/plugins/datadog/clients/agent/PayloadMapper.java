package org.datadog.jenkins.plugins.datadog.clients.agent;

public interface PayloadMapper<T> {

    byte[] map(final T obj);

    String contentType();
}
