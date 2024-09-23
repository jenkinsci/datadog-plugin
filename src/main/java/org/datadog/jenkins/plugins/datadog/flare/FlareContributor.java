package org.datadog.jenkins.plugins.datadog.flare;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FlareContributor {

    String getFilename();

    void writeFileContents(OutputStream out) throws IOException;

}
