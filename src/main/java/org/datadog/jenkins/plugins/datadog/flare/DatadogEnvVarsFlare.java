package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;

@Extension
public class DatadogEnvVarsFlare implements FlareContributor {

    @Override
    public int order() {
        return ORDER.ENV_VARS;
    }

    @Override
    public String getDescription() {
        return "DD_ and DATADOG_ environment variables";
    }

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

                String value = e.getValue();
                if (name.contains("API_KEY") || name.contains("APP_KEY")) {
                    value = "*".repeat(value.length());
                }

                datadogVariables.put(name, value);
            }
        }
        datadogVariables.store(out, "Environment variables prefixed with DD_ or DATADOG_");
    }

}
