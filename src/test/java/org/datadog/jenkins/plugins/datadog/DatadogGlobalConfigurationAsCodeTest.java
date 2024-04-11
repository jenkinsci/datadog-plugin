package org.datadog.jenkins.plugins.datadog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogApiConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogClientConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogCredentialsApiKey;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class DatadogGlobalConfigurationAsCodeTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("test-config.yml")
    public void testConfigurationAsCodeCompatibility() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        DatadogClientConfiguration datadogClientConfiguration = cfg.getDatadogClientConfiguration();
        Assert.assertTrue(datadogClientConfiguration instanceof DatadogApiConfiguration);

        DatadogApiConfiguration apiConfiguration = (DatadogApiConfiguration) datadogClientConfiguration;
        Assert.assertEquals("my-api-url", apiConfiguration.getApiUrl());
        Assert.assertEquals("my-log-intake-url", apiConfiguration.getLogIntakeUrl());
        Assert.assertEquals("my-webhook-intake-url", apiConfiguration.getWebhookIntakeUrl());

        assertTrue(apiConfiguration.getApiKey() instanceof DatadogCredentialsApiKey);
        DatadogCredentialsApiKey credentialsApiKey = (DatadogCredentialsApiKey) apiConfiguration.getApiKey();
        assertEquals("my-api-key-credentials-id", credentialsApiKey.getCredentialsId());

        Assert.assertTrue(cfg.getEnableCiVisibility());
        Assert.assertEquals("my-ci-instance-name", cfg.getCiInstanceName());
        Assert.assertEquals("my-excluded", cfg.getExcluded());
        Assert.assertEquals("my-included", cfg.getIncluded());
        Assert.assertEquals("my-hostname", cfg.getHostname());
        Assert.assertEquals("my-global-tag-file", cfg.getGlobalTagFile());
        Assert.assertEquals("my-global-tags", cfg.getGlobalTags());
        Assert.assertEquals("my-global-job-tags", cfg.getGlobalJobTags());
        Assert.assertEquals("my-include-events", cfg.getIncludeEvents());
        Assert.assertEquals("my-exclude-events", cfg.getExcludeEvents());
        Assert.assertTrue(cfg.isEmitSecurityEvents());
        Assert.assertTrue(cfg.isEmitSystemEvents());
        Assert.assertTrue(cfg.isCollectBuildLogs());
        Assert.assertTrue(cfg.isRetryLogs());
        Assert.assertTrue(cfg.isRefreshDogstatsdClient());
        Assert.assertTrue(cfg.isCacheBuildRuns());
        Assert.assertTrue(cfg.isUseAwsInstanceHostname());
    }

    @Test
    @ConfiguredWithCode("test-config-agent.yml")
    public void testAgentConfigurationAsCodeCompatibility() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        DatadogClientConfiguration datadogClientConfiguration = cfg.getDatadogClientConfiguration();
        Assert.assertTrue(datadogClientConfiguration instanceof DatadogAgentConfiguration);

        DatadogAgentConfiguration agentConfiguration = (DatadogAgentConfiguration) datadogClientConfiguration;
        Assert.assertEquals("my-agent-host", agentConfiguration.getAgentHost());
        Assert.assertEquals((Integer) 1357, agentConfiguration.getAgentPort());
        Assert.assertEquals((Integer) 2468, agentConfiguration.getAgentLogCollectionPort());
        Assert.assertEquals((Integer) 3579, agentConfiguration.getAgentTraceCollectionPort());

        Assert.assertTrue(cfg.getEnableCiVisibility());
        Assert.assertEquals("my-ci-instance-name", cfg.getCiInstanceName());
        Assert.assertEquals("my-excluded", cfg.getExcluded());
        Assert.assertEquals("my-included", cfg.getIncluded());
        Assert.assertEquals("my-hostname", cfg.getHostname());
        Assert.assertEquals("my-global-tag-file", cfg.getGlobalTagFile());
        Assert.assertEquals("my-global-tags", cfg.getGlobalTags());
        Assert.assertEquals("my-global-job-tags", cfg.getGlobalJobTags());
        Assert.assertEquals("my-include-events", cfg.getIncludeEvents());
        Assert.assertEquals("my-exclude-events", cfg.getExcludeEvents());
        Assert.assertFalse(cfg.isEmitSecurityEvents());
        Assert.assertFalse(cfg.isEmitSystemEvents());
        Assert.assertFalse(cfg.isCollectBuildLogs());
        Assert.assertFalse(cfg.isRetryLogs());
        Assert.assertFalse(cfg.isRefreshDogstatsdClient());
        Assert.assertFalse(cfg.isCacheBuildRuns());
        Assert.assertFalse(cfg.isUseAwsInstanceHostname());
    }

    @Test
    @ConfiguredWithCode("test-config-legacy.yml")
    public void testLegacyConfigurationAsCodeCompatibility() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        DatadogClientConfiguration datadogClientConfiguration = cfg.getDatadogClientConfiguration();
        Assert.assertTrue(datadogClientConfiguration instanceof DatadogApiConfiguration);

        DatadogApiConfiguration apiConfiguration = (DatadogApiConfiguration) datadogClientConfiguration;
        Assert.assertEquals("my-target-api-url", apiConfiguration.getApiUrl());
        Assert.assertEquals("my-target-log-intake-url", apiConfiguration.getLogIntakeUrl());
        Assert.assertEquals("my-target-webhook-intake-url", apiConfiguration.getWebhookIntakeUrl());

        assertTrue(apiConfiguration.getApiKey() instanceof DatadogCredentialsApiKey);
        DatadogCredentialsApiKey credentialsApiKey = (DatadogCredentialsApiKey) apiConfiguration.getApiKey();
        assertEquals("my-target-credentials-api-key", credentialsApiKey.getCredentialsId());

        Assert.assertTrue(cfg.getEnableCiVisibility());
        Assert.assertEquals("my-trace-service-name", cfg.getCiInstanceName());
        Assert.assertEquals("my-blacklist", cfg.getExcluded());
        Assert.assertEquals("my-whitelist", cfg.getIncluded());
        Assert.assertEquals("my-hostname", cfg.getHostname());
        Assert.assertEquals("my-global-tag-file", cfg.getGlobalTagFile());
        Assert.assertEquals("my-global-tags", cfg.getGlobalTags());
        Assert.assertEquals("my-global-job-tags", cfg.getGlobalJobTags());
        Assert.assertEquals("my-include-events", cfg.getIncludeEvents());
        Assert.assertEquals("my-exclude-events", cfg.getExcludeEvents());
        Assert.assertFalse(cfg.isEmitSecurityEvents());
        Assert.assertTrue(cfg.isEmitSystemEvents());
        Assert.assertFalse(cfg.isCollectBuildLogs());
        Assert.assertTrue(cfg.isRetryLogs());
        Assert.assertFalse(cfg.isRefreshDogstatsdClient());
        Assert.assertTrue(cfg.isCacheBuildRuns());
        Assert.assertFalse(cfg.isUseAwsInstanceHostname());
    }

    @Test
    @ConfiguredWithCode("test-config-legacy-agent.yml")
    public void testLegacyAgentConfigurationAsCodeCompatibility() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        DatadogClientConfiguration datadogClientConfiguration = cfg.getDatadogClientConfiguration();
        Assert.assertTrue(datadogClientConfiguration instanceof DatadogAgentConfiguration);

        DatadogAgentConfiguration agentConfiguration = (DatadogAgentConfiguration) datadogClientConfiguration;
        Assert.assertEquals("my-target-host", agentConfiguration.getAgentHost());
        Assert.assertEquals((Integer) 1357, agentConfiguration.getAgentPort());
        Assert.assertEquals((Integer) 2468, agentConfiguration.getAgentLogCollectionPort());
        Assert.assertEquals((Integer) 3579, agentConfiguration.getAgentTraceCollectionPort());

        Assert.assertFalse(cfg.getEnableCiVisibility());
        Assert.assertEquals("my-trace-service-name", cfg.getCiInstanceName());
        Assert.assertEquals("my-blacklist", cfg.getExcluded());
        Assert.assertEquals("my-whitelist", cfg.getIncluded());
        Assert.assertEquals("my-hostname", cfg.getHostname());
        Assert.assertEquals("my-global-tag-file", cfg.getGlobalTagFile());
        Assert.assertEquals("my-global-tags", cfg.getGlobalTags());
        Assert.assertEquals("my-global-job-tags", cfg.getGlobalJobTags());
        Assert.assertEquals("my-include-events", cfg.getIncludeEvents());
        Assert.assertEquals("my-exclude-events", cfg.getExcludeEvents());
        Assert.assertTrue(cfg.isEmitSecurityEvents());
        Assert.assertFalse(cfg.isEmitSystemEvents());
        Assert.assertTrue(cfg.isCollectBuildLogs());
        Assert.assertFalse(cfg.isRetryLogs());
        Assert.assertTrue(cfg.isRefreshDogstatsdClient());
        Assert.assertFalse(cfg.isCacheBuildRuns());
        Assert.assertTrue(cfg.isUseAwsInstanceHostname());
    }
}

