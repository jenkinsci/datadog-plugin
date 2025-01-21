package org.datadog.jenkins.plugins.datadog;

import com.thoughtworks.xstream.XStream;
import hudson.util.XStream2;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogApiConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * Verifies that configuration stays the same after being saved and then loaded
 */
@RunWith(Parameterized.class)
public class DatadogGlobalConfigurationSaveTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
                { "globalConfiguration.xml" },
                { "globalConfigurationCredentialsKey.xml" },
                { "globalConfigurationSite.xml" },
                { "globalConfigurationAgent.xml" },
                { "globalConfigurationLegacyFormat.xml" },
                { "globalConfigurationLegacyFormatCredentialsKey.xml" },
                { "globalConfigurationLegacyFormatAgent.xml" },
        });
    }

    private final String configurationResource;

    public DatadogGlobalConfigurationSaveTest(String configurationResource) {
        this.configurationResource = configurationResource;
    }

    @Test
    public void canSaveAndLoadGlobalConfiguration() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource(configurationResource);
        DatadogGlobalConfiguration loadedConfiguration = parseConfigurationFromString(serializeConfiguration(configuration));

        assertEquals(configuration.getDatadogClientConfiguration(), loadedConfiguration.getDatadogClientConfiguration());
        assertEquals(configuration.getExcluded(), loadedConfiguration.getExcluded());
        assertEquals(configuration.getIncluded(), loadedConfiguration.getIncluded());
        assertEquals(configuration.getCiInstanceName(), loadedConfiguration.getCiInstanceName());
        assertEquals(configuration.getHostname(), loadedConfiguration.getHostname());
        assertEquals(configuration.getGlobalTagFile(), loadedConfiguration.getGlobalTagFile());
        assertEquals(configuration.getGlobalTags(), loadedConfiguration.getGlobalTags());
        assertEquals(configuration.getGlobalJobTags(), loadedConfiguration.getGlobalJobTags());
        assertEquals(configuration.getIncludeEvents(), loadedConfiguration.getIncludeEvents());
        assertEquals(configuration.getExcludeEvents(), loadedConfiguration.getExcludeEvents());
        assertEquals(configuration.isEmitSecurityEvents(), loadedConfiguration.isEmitSecurityEvents());
        assertEquals(configuration.isEmitSystemEvents(), loadedConfiguration.isEmitSystemEvents());
        assertEquals(configuration.isCollectBuildLogs(), loadedConfiguration.isCollectBuildLogs());
        assertEquals(configuration.getEnableCiVisibility(), loadedConfiguration.getEnableCiVisibility());
        assertEquals(configuration.isRefreshDogstatsdClient(), loadedConfiguration.isRefreshDogstatsdClient());
        assertEquals(configuration.isCacheBuildRuns(), loadedConfiguration.isCacheBuildRuns());
        assertEquals(configuration.isUseAwsInstanceHostname(), loadedConfiguration.isUseAwsInstanceHostname());
        assertEquals(configuration.getTraceServiceName(), loadedConfiguration.getTraceServiceName());
        assertEquals(configuration.getBlacklist(), loadedConfiguration.getBlacklist());
        assertEquals(configuration.getWhitelist(), loadedConfiguration.getWhitelist());
        assertEquals(configuration.isCollectBuildTraces(), loadedConfiguration.isCollectBuildTraces());
        assertEquals(configuration.getReportWith(), loadedConfiguration.getReportWith());
        assertEquals(configuration.getTargetApiURL(), loadedConfiguration.getTargetApiURL());
        assertEquals(configuration.getTargetLogIntakeURL(), loadedConfiguration.getTargetLogIntakeURL());
        assertEquals(configuration.getTargetWebhookIntakeURL(), loadedConfiguration.getTargetWebhookIntakeURL());
        assertEquals(configuration.getTargetApiKey(), loadedConfiguration.getTargetApiKey());
        assertEquals(configuration.getTargetCredentialsApiKey(), loadedConfiguration.getTargetCredentialsApiKey());
        assertEquals(configuration.getTargetHost(), loadedConfiguration.getTargetHost());
        assertEquals(configuration.getTargetPort(), loadedConfiguration.getTargetPort());
        assertEquals(configuration.getTargetLogCollectionPort(), loadedConfiguration.getTargetLogCollectionPort());
        assertEquals(configuration.getTargetTraceCollectionPort(), loadedConfiguration.getTargetTraceCollectionPort());
    }

    @Test
    public void configurationUpdatesArePreservedOnReload() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource(configurationResource);
        configuration.setDatadogClientConfiguration(new DatadogAgentConfiguration("updated-host", 1000, 1001, 1002));

        DatadogGlobalConfiguration loadedConfiguration = parseConfigurationFromString(serializeConfiguration(configuration));

        assertEquals(configuration.getDatadogClientConfiguration(), loadedConfiguration.getDatadogClientConfiguration());
        assertEquals(configuration.getExcluded(), loadedConfiguration.getExcluded());
        assertEquals(configuration.getIncluded(), loadedConfiguration.getIncluded());
        assertEquals(configuration.getCiInstanceName(), loadedConfiguration.getCiInstanceName());
        assertEquals(configuration.getHostname(), loadedConfiguration.getHostname());
        assertEquals(configuration.getGlobalTagFile(), loadedConfiguration.getGlobalTagFile());
        assertEquals(configuration.getGlobalTags(), loadedConfiguration.getGlobalTags());
        assertEquals(configuration.getGlobalJobTags(), loadedConfiguration.getGlobalJobTags());
        assertEquals(configuration.getIncludeEvents(), loadedConfiguration.getIncludeEvents());
        assertEquals(configuration.getExcludeEvents(), loadedConfiguration.getExcludeEvents());
        assertEquals(configuration.isEmitSecurityEvents(), loadedConfiguration.isEmitSecurityEvents());
        assertEquals(configuration.isEmitSystemEvents(), loadedConfiguration.isEmitSystemEvents());
        assertEquals(configuration.isCollectBuildLogs(), loadedConfiguration.isCollectBuildLogs());
        assertEquals(configuration.getEnableCiVisibility(), loadedConfiguration.getEnableCiVisibility());
        assertEquals(configuration.isRefreshDogstatsdClient(), loadedConfiguration.isRefreshDogstatsdClient());
        assertEquals(configuration.isCacheBuildRuns(), loadedConfiguration.isCacheBuildRuns());
        assertEquals(configuration.isUseAwsInstanceHostname(), loadedConfiguration.isUseAwsInstanceHostname());
        assertEquals(configuration.getTraceServiceName(), loadedConfiguration.getTraceServiceName());
        assertEquals(configuration.getBlacklist(), loadedConfiguration.getBlacklist());
        assertEquals(configuration.getWhitelist(), loadedConfiguration.getWhitelist());
        assertEquals(configuration.isCollectBuildTraces(), loadedConfiguration.isCollectBuildTraces());
        assertEquals(configuration.getReportWith(), loadedConfiguration.getReportWith());
        assertEquals(configuration.getTargetApiURL(), loadedConfiguration.getTargetApiURL());
        assertEquals(configuration.getTargetLogIntakeURL(), loadedConfiguration.getTargetLogIntakeURL());
        assertEquals(configuration.getTargetWebhookIntakeURL(), loadedConfiguration.getTargetWebhookIntakeURL());
        assertEquals(configuration.getTargetApiKey(), loadedConfiguration.getTargetApiKey());
        assertEquals(configuration.getTargetCredentialsApiKey(), loadedConfiguration.getTargetCredentialsApiKey());
        assertEquals(configuration.getTargetHost(), loadedConfiguration.getTargetHost());
        assertEquals(configuration.getTargetPort(), loadedConfiguration.getTargetPort());
        assertEquals(configuration.getTargetLogCollectionPort(), loadedConfiguration.getTargetLogCollectionPort());
        assertEquals(configuration.getTargetTraceCollectionPort(), loadedConfiguration.getTargetTraceCollectionPort());
    }

    private static final XStream XSTREAM = new XStream2(XStream2.getDefaultDriver());

    static {
        XSTREAM.processAnnotations(new Class[] { DatadogGlobalConfiguration.class, DatadogApiConfiguration.class });
    }

    private static DatadogGlobalConfiguration parseConfigurationFromResource(String resourceName) {
        URL resource = DatadogGlobalConfigurationSaveTest.class.getResource(resourceName);
        return (DatadogGlobalConfiguration) XSTREAM.fromXML(resource);
    }

    private static DatadogGlobalConfiguration parseConfigurationFromString(String serializedConfiguration) {
        return (DatadogGlobalConfiguration) XSTREAM.fromXML(serializedConfiguration);
    }

    private static String serializeConfiguration(DatadogGlobalConfiguration configuration) {
        return XSTREAM.toXML(configuration);
    }
}
