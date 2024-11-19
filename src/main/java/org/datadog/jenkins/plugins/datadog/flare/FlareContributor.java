package org.datadog.jenkins.plugins.datadog.flare;

import java.io.OutputStream;

public interface FlareContributor {

    interface ORDER {
        int RUNTIME_INFO = 0;
        int CONFIG = 1;
        int CONNECTIVITY_CHECKS = 2;
        int ENV_VARS = 3;
        int WRITERS_HEALTH = 4;
        int EXCEPTIONS = 5;
        int PLUGIN_LOGS = 6;
        int JENKINS_LOGS = 7;
        int THREAD_DUMP = 8;
        int JFR = 9;
    }

    default boolean isEnabledByDefault() {
        return true;
    }

    int order();

    String getDescription();

    String getFilename();

    void writeFileContents(OutputStream out) throws Exception;

}
