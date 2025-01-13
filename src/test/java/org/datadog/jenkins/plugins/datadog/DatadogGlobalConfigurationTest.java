package org.datadog.jenkins.plugins.datadog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.thoughtworks.xstream.XStream;
import hudson.util.Secret;
import hudson.util.XStream2;
import java.net.URL;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogApiConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntake;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogSite;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogCredentialsApiKey;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogTextApiKey;
import org.junit.Test;

public class DatadogGlobalConfigurationTest {

    @Test
    public void canLoadGlobalConfiguration() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource("globalConfiguration.xml");
        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals("https://my-api-url.com/api/", intake.getApiUrl());
        assertEquals("https://my-log-intake-url.com/v1/input/", intake.getLogsUrl());
        assertEquals("https://my-webhook-intake-url.com/api/v2/webhook/", intake.getWebhooksUrl());

        assertTrue(datadogClientConfiguration.getApiKey() instanceof DatadogTextApiKey);
        DatadogTextApiKey textApiKey = (DatadogTextApiKey) datadogClientConfiguration.getApiKey();
        assertEquals("{AQAAABAAAAAwB78atDHwzelG9W0Fw5FRNq0ZUaLh1BwpQPKhvs2u84lxkRPDqeUNoVZel+MKwfyTOjuetnituHYGMdvE9bc3kg==}", Secret.toString(textApiKey.getKey()));

        assertEquals("my-jenkins-service-name", configuration.getCiInstanceName());
        assertEquals("my-hostname", configuration.getHostname());
        assertEquals("my-blacklist", configuration.getExcluded());
        assertEquals("my-whitelist", configuration.getIncluded());
        assertEquals("my-global-tag-file", configuration.getGlobalTagFile());
        assertEquals("my-global-tags", configuration.getGlobalTags());
        assertEquals("my-global-job-tags", configuration.getGlobalJobTags());
        assertEquals("my-include-events", configuration.getIncludeEvents());
        assertEquals("my-exclude-evens", configuration.getExcludeEvents());
        assertFalse(configuration.isEmitSecurityEvents());
        assertFalse(configuration.isEmitSystemEvents());
        assertTrue(configuration.isCollectBuildLogs());
        assertTrue(configuration.getEnableCiVisibility());
        assertTrue(configuration.isRefreshDogstatsdClient());
        assertFalse(configuration.isCacheBuildRuns());
        assertTrue(configuration.isUseAwsInstanceHostname());
    }

    @Test
    public void canLoadGlobalConfigurationWithCredentialsApiKey() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource("globalConfigurationCredentialsKey.xml");
        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals("https://my-api-url.com/api/", intake.getApiUrl());
        assertEquals("https://my-log-intake-url.com/v1/input/", intake.getLogsUrl());
        assertEquals("https://my-webhook-intake-url.com/api/v2/webhook/", intake.getWebhooksUrl());

        assertTrue(datadogClientConfiguration.getApiKey() instanceof DatadogCredentialsApiKey);
        DatadogCredentialsApiKey credentialsApiKey = (DatadogCredentialsApiKey) datadogClientConfiguration.getApiKey();
        assertEquals("my-api-key-credentials-id", credentialsApiKey.getCredentialsId());

        assertEquals("my-jenkins-service-name", configuration.getCiInstanceName());
        assertEquals("my-hostname", configuration.getHostname());
        assertEquals("my-blacklist", configuration.getExcluded());
        assertEquals("my-whitelist", configuration.getIncluded());
        assertEquals("my-global-tag-file", configuration.getGlobalTagFile());
        assertEquals("my-global-tags", configuration.getGlobalTags());
        assertEquals("my-global-job-tags", configuration.getGlobalJobTags());
        assertEquals("my-include-events", configuration.getIncludeEvents());
        assertEquals("my-exclude-evens", configuration.getExcludeEvents());
        assertFalse(configuration.isEmitSecurityEvents());
        assertFalse(configuration.isEmitSystemEvents());
        assertTrue(configuration.isCollectBuildLogs());
        assertTrue(configuration.getEnableCiVisibility());
        assertTrue(configuration.isRefreshDogstatsdClient());
        assertFalse(configuration.isCacheBuildRuns());
        assertTrue(configuration.isUseAwsInstanceHostname());
    }

    @Test
    public void canLoadGlobalConfigurationWithSite() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource("globalConfigurationSite.xml");
        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals(DatadogSite.US3.getApiUrl(), intake.getApiUrl());
        assertEquals(DatadogSite.US3.getLogsUrl(), intake.getLogsUrl());
        assertEquals(DatadogSite.US3.getWebhooksUrl(), intake.getWebhooksUrl());

        assertTrue(datadogClientConfiguration.getApiKey() instanceof DatadogCredentialsApiKey);
        DatadogCredentialsApiKey credentialsApiKey = (DatadogCredentialsApiKey) datadogClientConfiguration.getApiKey();
        assertEquals("my-api-key-credentials-id", credentialsApiKey.getCredentialsId());

        assertEquals("my-jenkins-service-name", configuration.getCiInstanceName());
        assertEquals("my-hostname", configuration.getHostname());
        assertEquals("my-blacklist", configuration.getExcluded());
        assertEquals("my-whitelist", configuration.getIncluded());
        assertEquals("my-global-tag-file", configuration.getGlobalTagFile());
        assertEquals("my-global-tags", configuration.getGlobalTags());
        assertEquals("my-global-job-tags", configuration.getGlobalJobTags());
        assertEquals("my-include-events", configuration.getIncludeEvents());
        assertEquals("my-exclude-evens", configuration.getExcludeEvents());
        assertFalse(configuration.isEmitSecurityEvents());
        assertFalse(configuration.isEmitSystemEvents());
        assertTrue(configuration.isCollectBuildLogs());
        assertTrue(configuration.getEnableCiVisibility());
        assertTrue(configuration.isRefreshDogstatsdClient());
        assertFalse(configuration.isCacheBuildRuns());
        assertTrue(configuration.isUseAwsInstanceHostname());
    }

    @Test
    public void canLoadGlobalConfigurationReportingToAgent() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource("globalConfigurationAgent.xml");

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogAgentConfiguration);
        DatadogAgentConfiguration datadogAgentConfiguration = (DatadogAgentConfiguration) configuration.getDatadogClientConfiguration();
        assertEquals("my-agent-host", datadogAgentConfiguration.getAgentHost());
        assertEquals((Integer) 9876, datadogAgentConfiguration.getAgentPort());
        assertEquals((Integer) 8765, datadogAgentConfiguration.getAgentLogCollectionPort());
        assertEquals((Integer) 7654, datadogAgentConfiguration.getAgentTraceCollectionPort());

        assertEquals("my-jenkins-service-name", configuration.getCiInstanceName());
        assertEquals("my-hostname", configuration.getHostname());
        assertEquals("my-blacklist", configuration.getExcluded());
        assertEquals("my-whitelist", configuration.getIncluded());
        assertEquals("my-global-tag-file", configuration.getGlobalTagFile());
        assertEquals("my-global-tags", configuration.getGlobalTags());
        assertEquals("my-global-job-tags", configuration.getGlobalJobTags());
        assertEquals("my-include-events", configuration.getIncludeEvents());
        assertEquals("my-exclude-evens", configuration.getExcludeEvents());
        assertTrue(configuration.isEmitSecurityEvents());
        assertTrue(configuration.isEmitSystemEvents());
        assertTrue(configuration.isCollectBuildLogs());
        assertTrue(configuration.getEnableCiVisibility());
        assertTrue(configuration.isRefreshDogstatsdClient());
        assertTrue(configuration.isCacheBuildRuns());
        assertTrue(configuration.isUseAwsInstanceHostname());
    }

    @Test
    public void canLoadGlobalConfigurationFromLegacyFormat() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource("globalConfigurationLegacyFormat.xml");
        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals("my-target-api-url", intake.getApiUrl());
        assertEquals("my-target-log-intake-url", intake.getLogsUrl());
        assertEquals("my-target-webhook-intake-url", intake.getWebhooksUrl());

        assertTrue(datadogClientConfiguration.getApiKey() instanceof DatadogTextApiKey);
        DatadogTextApiKey textApiKey = (DatadogTextApiKey) datadogClientConfiguration.getApiKey();
        assertEquals("{AQAAABAAAAAwB78atDHwzelG9W0Fw5FRNq0ZUaLh1BwpQPKhvs2u84lxkRPDqeUNoVZel+MKwfyTOjuetnituHYGMdvE9bc3kg==}", Secret.toString(textApiKey.getKey()));

        assertEquals("my-jenkins-service-name", configuration.getCiInstanceName());
        assertEquals("my-hostname", configuration.getHostname());
        assertEquals("my-blacklist", configuration.getExcluded());
        assertEquals("my-whitelist", configuration.getIncluded());
        assertEquals("my-global-tag-file", configuration.getGlobalTagFile());
        assertEquals("my-global-tags", configuration.getGlobalTags());
        assertEquals("my-global-job-tags", configuration.getGlobalJobTags());
        assertEquals("my-include-events", configuration.getIncludeEvents());
        assertEquals("my-exclude-evens", configuration.getExcludeEvents());
        assertFalse(configuration.isEmitSecurityEvents());
        assertFalse(configuration.isEmitSystemEvents());
        assertTrue(configuration.isCollectBuildLogs());
        assertTrue(configuration.getEnableCiVisibility());
        assertTrue(configuration.isRefreshDogstatsdClient());
        assertFalse(configuration.isCacheBuildRuns());
        assertTrue(configuration.isUseAwsInstanceHostname());
    }

    @Test
    public void canLoadGlobalConfigurationFromLegacyFormatWithCredentialsApiKey() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource("globalConfigurationLegacyFormatCredentialsKey.xml");
        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals("my-target-api-url", intake.getApiUrl());
        assertEquals("my-target-log-intake-url", intake.getLogsUrl());
        assertEquals("my-target-webhook-intake-url", intake.getWebhooksUrl());

        assertTrue(datadogClientConfiguration.getApiKey() instanceof DatadogCredentialsApiKey);
        DatadogCredentialsApiKey credentialsApiKey = (DatadogCredentialsApiKey) datadogClientConfiguration.getApiKey();
        assertEquals("target-credentials-api-key", credentialsApiKey.getCredentialsId());

        assertEquals("my-jenkins-service-name", configuration.getCiInstanceName());
        assertEquals("my-hostname", configuration.getHostname());
        assertEquals("my-blacklist", configuration.getExcluded());
        assertEquals("my-whitelist", configuration.getIncluded());
        assertEquals("my-global-tag-file", configuration.getGlobalTagFile());
        assertEquals("my-global-tags", configuration.getGlobalTags());
        assertEquals("my-global-job-tags", configuration.getGlobalJobTags());
        assertEquals("my-include-events", configuration.getIncludeEvents());
        assertEquals("my-exclude-evens", configuration.getExcludeEvents());
        assertFalse(configuration.isEmitSecurityEvents());
        assertFalse(configuration.isEmitSystemEvents());
        assertTrue(configuration.isCollectBuildLogs());
        assertTrue(configuration.getEnableCiVisibility());
        assertTrue(configuration.isRefreshDogstatsdClient());
        assertFalse(configuration.isCacheBuildRuns());
        assertTrue(configuration.isUseAwsInstanceHostname());
    }

    @Test
    public void canLoadGlobalConfigurationFromLegacyFormatReportingToAgent() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource("globalConfigurationLegacyFormatAgent.xml");

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogAgentConfiguration);
        DatadogAgentConfiguration datadogAgentConfiguration = (DatadogAgentConfiguration) configuration.getDatadogClientConfiguration();
        assertEquals("datadog", datadogAgentConfiguration.getAgentHost());
        assertEquals((Integer) 1357, datadogAgentConfiguration.getAgentPort());
        assertEquals((Integer) 2468, datadogAgentConfiguration.getAgentLogCollectionPort());
        assertEquals((Integer) 3579, datadogAgentConfiguration.getAgentTraceCollectionPort());

        assertEquals("my-jenkins-service-name", configuration.getCiInstanceName());
        assertEquals("my-hostname", configuration.getHostname());
        assertEquals("my-blacklist", configuration.getExcluded());
        assertEquals("my-whitelist", configuration.getIncluded());
        assertEquals("my-global-tag-file", configuration.getGlobalTagFile());
        assertEquals("my-global-tags", configuration.getGlobalTags());
        assertEquals("my-global-job-tags", configuration.getGlobalJobTags());
        assertEquals("my-include-events", configuration.getIncludeEvents());
        assertEquals("my-exclude-evens", configuration.getExcludeEvents());
        assertTrue(configuration.isEmitSecurityEvents());
        assertTrue(configuration.isEmitSystemEvents());
        assertFalse(configuration.isCollectBuildLogs());
        assertFalse(configuration.getEnableCiVisibility());
        assertFalse(configuration.isRefreshDogstatsdClient());
        assertTrue(configuration.isCacheBuildRuns());
        assertFalse(configuration.isUseAwsInstanceHostname());
    }

    @Test
    public void canLoadGlobalConfigurationFromLegacyFormatWithEmptyCredentialsKey() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource("globalConfigurationLegacyFormatCredentialsKeyEmpty.xml");
        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals("my-target-api-url", intake.getApiUrl());
        assertEquals("my-target-log-intake-url", intake.getLogsUrl());
        assertEquals("my-target-webhook-intake-url", intake.getWebhooksUrl());

        assertTrue(datadogClientConfiguration.getApiKey() instanceof DatadogTextApiKey);
        DatadogTextApiKey textApiKey = (DatadogTextApiKey) datadogClientConfiguration.getApiKey();
        assertEquals("{AQAAABAAAAAwB78atDHwzelG9W0Fw5FRNq0ZUaLh1BwpQPKhvs2u84lxkRPDqeUNoVZel+MKwfyTOjuetnituHYGMdvE9bc3kg==}", Secret.toString(textApiKey.getKey()));

        assertEquals("my-jenkins-service-name", configuration.getCiInstanceName());
        assertEquals("my-hostname", configuration.getHostname());
        assertEquals("my-blacklist", configuration.getExcluded());
        assertEquals("my-whitelist", configuration.getIncluded());
        assertEquals("my-global-tag-file", configuration.getGlobalTagFile());
        assertEquals("my-global-tags", configuration.getGlobalTags());
        assertEquals("my-global-job-tags", configuration.getGlobalJobTags());
        assertEquals("my-include-events", configuration.getIncludeEvents());
        assertEquals("my-exclude-evens", configuration.getExcludeEvents());
        assertFalse(configuration.isEmitSecurityEvents());
        assertFalse(configuration.isEmitSystemEvents());
        assertTrue(configuration.isCollectBuildLogs());
        assertTrue(configuration.getEnableCiVisibility());
        assertTrue(configuration.isRefreshDogstatsdClient());
        assertFalse(configuration.isCacheBuildRuns());
        assertTrue(configuration.isUseAwsInstanceHostname());
    }

    @Test
    public void canLoadGlobalConfigurationThatContainsInvalidExcludeRegex() {
        DatadogGlobalConfiguration configuration = parseConfigurationFromResource("globalConfigurationInvalidExcludeRegex.xml");
        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals("https://my-api-url.com/api/", intake.getApiUrl());
        assertEquals("https://my-log-intake-url.com/v1/input/", intake.getLogsUrl());
        assertEquals("https://my-webhook-intake-url.com/api/v2/webhook/", intake.getWebhooksUrl());

        assertTrue(datadogClientConfiguration.getApiKey() instanceof DatadogTextApiKey);
        DatadogTextApiKey textApiKey = (DatadogTextApiKey) datadogClientConfiguration.getApiKey();
        assertEquals("{AQAAABAAAAAwB78atDHwzelG9W0Fw5FRNq0ZUaLh1BwpQPKhvs2u84lxkRPDqeUNoVZel+MKwfyTOjuetnituHYGMdvE9bc3kg==}", Secret.toString(textApiKey.getKey()));

        assertEquals("my-jenkins-service-name", configuration.getCiInstanceName());
        assertEquals("my-hostname", configuration.getHostname());
        assertNull(configuration.getExcluded()); // this is null since the regex is invalid
        assertEquals("my-whitelist", configuration.getIncluded());
        assertEquals("my-global-tag-file", configuration.getGlobalTagFile());
        assertEquals("my-global-tags", configuration.getGlobalTags());
        assertEquals("my-global-job-tags", configuration.getGlobalJobTags());
        assertEquals("my-include-events", configuration.getIncludeEvents());
        assertEquals("my-exclude-evens", configuration.getExcludeEvents());
        assertFalse(configuration.isEmitSecurityEvents());
        assertFalse(configuration.isEmitSystemEvents());
        assertTrue(configuration.isCollectBuildLogs());
        assertTrue(configuration.getEnableCiVisibility());
        assertTrue(configuration.isRefreshDogstatsdClient());
        assertFalse(configuration.isCacheBuildRuns());
        assertTrue(configuration.isUseAwsInstanceHostname());
    }

    private static final XStream XSTREAM = new XStream2(XStream2.getDefaultDriver());

    static {
        XSTREAM.processAnnotations(new Class[] { DatadogGlobalConfiguration.class, DatadogApiConfiguration.class });
    }

    private static DatadogGlobalConfiguration parseConfigurationFromResource(String resourceName) {
        URL resource = DatadogGlobalConfigurationTest.class.getResource(resourceName);
        return (DatadogGlobalConfiguration) XSTREAM.fromXML(resource);
    }
}
