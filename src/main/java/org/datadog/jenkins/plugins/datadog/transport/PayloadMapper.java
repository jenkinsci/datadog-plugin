package org.datadog.jenkins.plugins.datadog.transport;

public interface PayloadMapper<T extends PayloadMessage> {

    byte[] map(final T obj);

    String contentType();
}
