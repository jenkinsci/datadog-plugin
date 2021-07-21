package org.datadog.jenkins.plugins.datadog.transport;

public interface HttpClient {

    void send(PayloadMessage obj);

    void stop();

    void close();
}
