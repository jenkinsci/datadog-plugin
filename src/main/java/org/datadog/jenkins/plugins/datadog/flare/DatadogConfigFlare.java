package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import java.io.IOException;
import java.io.OutputStream;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

@Extension
public class DatadogConfigFlare implements FlareContributor {

    @Override
    public int order() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Plugin configuration";
    }

    @Override
    public String getFilename() {
        return "DatadogGlobalConfiguration.xml";
    }

    @Override
    public void writeFileContents(OutputStream out) throws IOException {
        DatadogGlobalConfiguration globalConfiguration = DatadogUtilities.getDatadogGlobalDescriptor();
        DatadogGlobalConfiguration.XSTREAM.toXMLUTF8(globalConfiguration, out);
    }
}
