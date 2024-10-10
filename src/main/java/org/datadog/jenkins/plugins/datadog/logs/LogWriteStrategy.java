package org.datadog.jenkins.plugins.datadog.logs;

import java.util.List;

public interface LogWriteStrategy {

    LogWriteStrategy NO_OP = new LogWriteStrategy() {
        @Override
        public void send(List<String> logs) {
            // no op
        }

        @Override
        public void close() {
            // no op
        }
    };

    void send(List<String> logs);
    void close();
}
