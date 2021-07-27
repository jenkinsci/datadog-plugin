package org.datadog.jenkins.plugins.datadog.transport;

import java.util.List;

public interface HttpClient {

    void send(List<PayloadMessage> messages);

    void stop();

    void close();
}
