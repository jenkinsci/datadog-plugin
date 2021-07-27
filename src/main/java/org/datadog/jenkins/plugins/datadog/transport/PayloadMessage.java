package org.datadog.jenkins.plugins.datadog.transport;

public interface PayloadMessage {

    Type getMessageType();

    enum Type {
        TRACE
    }
}
