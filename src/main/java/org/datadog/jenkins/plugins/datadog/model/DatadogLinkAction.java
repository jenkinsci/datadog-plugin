package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.model.Action;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter.ignoreOldData;

public class DatadogLinkAction implements Action {

    private final String url;

    public DatadogLinkAction(BuildData buildData, String siteName) {
        String query = String.format("ci_level:pipeline @ci.pipeline.name:\"%s\" @ci.pipeline.number:%s", buildData.getJobName(), buildData.getBuildNumber(""));
        String urlEncodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        this.url = String.format("https://app.%s/ci/pipeline-executions?query=%s", siteName, urlEncodedQuery);
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

    public static final class ConverterImpl extends DatadogConverter<DatadogLinkAction> {
        public ConverterImpl(XStream xs) {
            super(ignoreOldData(), new ConverterV1());
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
