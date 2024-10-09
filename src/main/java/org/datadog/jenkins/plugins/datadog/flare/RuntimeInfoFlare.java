package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@Extension
public class RuntimeInfoFlare implements FlareContributor {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public String getDescription() {
        return "Runtime information (versions of JDK, Jenkins, plugin)";
    }

    @Override
    public String getFilename() {
        return "runtime-info.json";
    }

    @Override
    public void writeFileContents(OutputStream out) throws IOException {
        JSONObject payload = new JSONObject();
        payload.put("hostname", DatadogUtilities.getHostname(null));
        payload.put("java-runtime-name", System.getProperty("java.runtime.name"));
        payload.put("java-version", System.getProperty("java.version"));
        payload.put("java-vendor", System.getProperty("java.vendor"));
        payload.put("os-architecture", System.getProperty("os.arch"));
        payload.put("os-name", System.getProperty("os.name"));
        payload.put("os-version", System.getProperty("os.version"));
        payload.put("jenkins-version", String.valueOf(Jenkins.getVersion()));
        payload.put("plugin-version", DatadogUtilities.getDatadogPluginVersion());

        String payloadString = payload.toString(2);
        IOUtils.write(payloadString, out, StandardCharsets.UTF_8);
    }
}
