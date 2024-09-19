package org.datadog.jenkins.plugins.datadog.logs;

import java.util.List;

public interface LogWriteStrategy {
    void send(List<String> logs);
    void close();
}
