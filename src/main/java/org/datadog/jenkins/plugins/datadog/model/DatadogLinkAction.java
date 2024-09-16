package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.model.Action;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogActionConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class DatadogLinkAction implements Action {

    private final String url;

    public DatadogLinkAction(BuildData buildData) { // ci.pipeline.url%3A"https%3A%2F%2Fgoogle.com"
        String query = String.format("ci_level:pipeline @ci.pipeline.name:\"%s\" @ci.pipeline.number:%s", buildData.getJobName(), buildData.getBuildNumber(""));
        String urlEncodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        this.url = String.format("https://app.datadoghq.com/ci/pipeline-executions?query=%s", urlEncodedQuery);
    }

    private DatadogLinkAction(String url) {
        this.url = url;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/datadog/icons/dd_icon_rgb.svg";
    }

    @Override
    public String getDisplayName() {
        return "View in Datadog";
    }

    @Override
    public String getUrlName() {
        return url;
    }

    public static final class ConverterImpl extends DatadogActionConverter<DatadogLinkAction> {
        public ConverterImpl(XStream xs) {
            super(new ConverterV1());
        }
    }

    public static final class ConverterV1 extends VersionedConverter<DatadogLinkAction> {
        private static final int VERSION = 1;

        public ConverterV1() {
            super(VERSION);
        }

        @Override
        public void marshal(DatadogLinkAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            writeField("url", action.url, writer, context);
        }

        @Override
        public DatadogLinkAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            String url = readField(reader, context, String.class);
            return new DatadogLinkAction(url);
        }
    }
}
