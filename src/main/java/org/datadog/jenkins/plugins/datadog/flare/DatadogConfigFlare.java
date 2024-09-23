package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import hudson.util.XStream2;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import java.io.IOException;
import java.io.OutputStream;

@Extension
public class DatadogConfigFlare implements FlareContributor {

    // TODO use XSTREAM from DatadogGlobalConfiguration following configuration refactor
    private static final XStream2 XSTREAM;

    static {
        XSTREAM = new XStream2();
        XSTREAM.autodetectAnnotations(true);
    }

    @Override
    public String getFilename() {
        return "DatadogGlobalConfiguration.xml";
    }

    @Override
    public void writeFileContents(OutputStream out) throws IOException {
        DatadogGlobalConfiguration globalConfiguration = DatadogUtilities.getDatadogGlobalDescriptor();
        XSTREAM.toXMLUTF8(globalConfiguration, out);
    }
}
