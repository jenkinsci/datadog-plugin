package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Extension
public class JfrFlare implements FlareContributor {

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public int order() {
        return 8;
    }

    @Override
    public String getDescription() {
        return "JFR recording (30 seconds)";
    }

    @Override
    public String getFilename() {
        return "record.jfr";
    }

    @Override
    public void writeFileContents(OutputStream out) throws Exception {
        Path temporaryFile = Files.createTempFile("dd-jenkins-plugin", ".jfr");

        Configuration config = Configuration.getConfiguration("profile");
        try (Recording recording = new Recording(config)) {
            recording.start();
            Thread.sleep(TimeUnit.SECONDS.toMillis(30));  // Record for 30 seconds
            recording.stop();
            recording.dump(temporaryFile);

            try (InputStream in = Files.newInputStream(temporaryFile)) {
                IOUtils.copy(in, out);
            }
        } finally {
            Files.delete(temporaryFile);
        }
    }
}
