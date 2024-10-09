package org.datadog.jenkins.plugins.datadog.flare;

import java.io.OutputStream;

public interface FlareContributor {

    default boolean isEnabledByDefault() {
        return true;
    }

    int order();

    String getDescription();

    String getFilename();

    void writeFileContents(OutputStream out) throws Exception;

}
