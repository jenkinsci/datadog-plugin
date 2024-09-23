package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;

@Extension
public class DatadogEnvVarsFlare implements FlareContributor {

    @Override
    public String getFilename() {
        return "dd-env-vars.properties";
    }

    @Override
    public void writeFileContents(OutputStream out) throws IOException {
        Properties datadogVariables = new Properties();
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            String name = e.getKey();
            if (name.startsWith("DD_") || name.startsWith("DATADOG_")) {
                if (!name.contains("API_KEY") && !name.contains("APP_KEY")) {
                    datadogVariables.put(name, e.getValue());
                }
            }
        }
        datadogVariables.store(out, "Environment variables prefixed with DD_ or DATADOG_");
    }

}
