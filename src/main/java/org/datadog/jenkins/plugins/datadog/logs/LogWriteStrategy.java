package org.datadog.jenkins.plugins.datadog.logs;

import net.sf.json.JSONObject;
import java.util.List;

public interface LogWriteStrategy {

    LogWriteStrategy NO_OP = new LogWriteStrategy() {
        @Override
        public void send(List<JSONObject> logs) {
            // no op
        }

        @Override
        public void close() {
            // no op
        }
    };

    void send(List<JSONObject> logs);
    void close();
}
