package org.datadog.jenkins.plugins.datadog.flare;

import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import hudson.Extension;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import org.datadog.jenkins.plugins.datadog.util.AsyncWriter;

@Extension
public class WritersHealthFlare implements FlareContributor {

    private final ObjectMapper objectMapper;

    public WritersHealthFlare() {
        objectMapper = new ObjectMapper()
                .registerModule(new MetricsModule(TimeUnit.SECONDS, TimeUnit.MILLISECONDS, false))
                .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public String getDescription() {
        return "Health stats for async writers (traces, logs)";
    }

    @Override
    public String getFilename() {
        return "writers-health.json";
    }

    @Override
    public void writeFileContents(OutputStream out) throws IOException {
        objectMapper.writeValue(out, AsyncWriter.METRICS);
    }
}
