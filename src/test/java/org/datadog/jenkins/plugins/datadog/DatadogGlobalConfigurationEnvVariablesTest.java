package org.datadog.jenkins.plugins.datadog;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogApiConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntake;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeSite;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogSite;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogApiKey;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogTextApiKey;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class DatadogGlobalConfigurationEnvVariablesTest {

    @ClassRule
    public static final JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testDefaultValues() throws Exception {
        DatadogGlobalConfiguration configuration = SystemLambda
                .withEnvironmentVariable(DatadogGlobalConfiguration.REPORT_WITH_PROPERTY, null)
                .and(DatadogIntakeSite.DATADOG_SITE_PROPERTY, null)
                .and(DatadogIntakeUrls.TARGET_API_URL_PROPERTY, null)
                .and(DatadogIntakeUrls.TARGET_WEBHOOK_INTAKE_URL_PROPERTY, null)
                .and(DatadogIntakeUrls.TARGET_LOG_INTAKE_URL_PROPERTY, null)
                .and(DatadogTextApiKey.TARGET_API_KEY_PROPERTY, null)
                .execute(DatadogGlobalConfiguration::new);

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals("https://api.datadoghq.com/api/", intake.getApiUrl());
        assertEquals("https://http-intake.logs.datadoghq.com/v1/input/", intake.getLogsUrl());
        assertEquals("https://webhook-intake.datadoghq.com/api/v2/webhook/", intake.getWebhooksUrl());

        DatadogApiKey apiKey = datadogClientConfiguration.getApiKey();
        assertNull(apiKey.getKey());
    }

    @Test
    public void testAgentDefaultValues() throws Exception {
        DatadogGlobalConfiguration configuration = SystemLambda
                .withEnvironmentVariable(DatadogGlobalConfiguration.REPORT_WITH_PROPERTY, "DSD")
                .and(DatadogAgentConfiguration.TARGET_HOST_PROPERTY, null)
                .and(DatadogAgentConfiguration.TARGET_PORT_PROPERTY, null)
                .and(DatadogAgentConfiguration.TARGET_TRACE_COLLECTION_PORT_PROPERTY, null)
                .and(DatadogAgentConfiguration.TARGET_LOG_COLLECTION_PORT_PROPERTY, null)
                .and(DatadogAgentConfiguration.DD_AGENT_HOST, null)
                .and(DatadogAgentConfiguration.DD_AGENT_PORT, null)
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_PORT, null)
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_URL, null)
                .execute(DatadogGlobalConfiguration::new);

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogAgentConfiguration);
        DatadogAgentConfiguration datadogClientConfiguration = (DatadogAgentConfiguration) configuration.getDatadogClientConfiguration();

        assertEquals("localhost", datadogClientConfiguration.getAgentHost());
        assertEquals((Integer) 8125, datadogClientConfiguration.getAgentPort());
        assertEquals((Integer) 8126, datadogClientConfiguration.getAgentTraceCollectionPort());
        assertEquals((Integer) null, datadogClientConfiguration.getAgentLogCollectionPort());
    }

    @Test
    public void testIntakeConfiguration() throws Exception {
        DatadogGlobalConfiguration configuration = SystemLambda
                .withEnvironmentVariable(DatadogGlobalConfiguration.REPORT_WITH_PROPERTY, "HTTP")
                .and(DatadogIntakeSite.DATADOG_SITE_PROPERTY, null)
                .and(DatadogIntakeUrls.TARGET_API_URL_PROPERTY, "my-target-api-url")
                .and(DatadogIntakeUrls.TARGET_WEBHOOK_INTAKE_URL_PROPERTY, "my-webhook-intake-url")
                .and(DatadogIntakeUrls.TARGET_LOG_INTAKE_URL_PROPERTY, "my-log-intake-url")
                .and(DatadogTextApiKey.TARGET_API_KEY_PROPERTY, "my-api-key")
                .execute(DatadogGlobalConfiguration::new);

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals("my-target-api-url", intake.getApiUrl());
        assertEquals("my-log-intake-url", intake.getLogsUrl());
        assertEquals("my-webhook-intake-url", intake.getWebhooksUrl());

        DatadogApiKey apiKey = datadogClientConfiguration.getApiKey();
        assertEquals("my-api-key", apiKey.getKey().getPlainText());
    }

    @Test
    public void testSiteConfiguration() throws Exception {
        DatadogGlobalConfiguration configuration = SystemLambda
                .withEnvironmentVariable(DatadogGlobalConfiguration.REPORT_WITH_PROPERTY, "HTTP")
                .and(DatadogIntakeSite.DATADOG_SITE_PROPERTY, "US1")
                .and(DatadogIntakeUrls.TARGET_API_URL_PROPERTY, null)
                .and(DatadogIntakeUrls.TARGET_WEBHOOK_INTAKE_URL_PROPERTY, null)
                .and(DatadogIntakeUrls.TARGET_LOG_INTAKE_URL_PROPERTY, null)
                .and(DatadogTextApiKey.TARGET_API_KEY_PROPERTY, "my-api-key")
                .execute(DatadogGlobalConfiguration::new);

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogApiConfiguration);
        DatadogApiConfiguration datadogClientConfiguration = (DatadogApiConfiguration) configuration.getDatadogClientConfiguration();

        DatadogIntake intake = datadogClientConfiguration.getIntake();
        assertEquals(DatadogSite.US1.getApiUrl(), intake.getApiUrl());
        assertEquals(DatadogSite.US1.getLogsUrl(), intake.getLogsUrl());
        assertEquals(DatadogSite.US1.getWebhooksUrl(), intake.getWebhooksUrl());

        DatadogApiKey apiKey = datadogClientConfiguration.getApiKey();
        assertEquals("my-api-key", apiKey.getKey().getPlainText());
    }

    @Test
    public void testAgentConfiguration() throws Exception {
        DatadogGlobalConfiguration configuration = SystemLambda
                .withEnvironmentVariable(DatadogGlobalConfiguration.REPORT_WITH_PROPERTY, "DSD")
                .and(DatadogAgentConfiguration.TARGET_HOST_PROPERTY, "my-target-host")
                .and(DatadogAgentConfiguration.TARGET_PORT_PROPERTY, "123")
                .and(DatadogAgentConfiguration.TARGET_TRACE_COLLECTION_PORT_PROPERTY, "456")
                .and(DatadogAgentConfiguration.TARGET_LOG_COLLECTION_PORT_PROPERTY, "789")
                .and(DatadogAgentConfiguration.DD_AGENT_HOST, null)
                .and(DatadogAgentConfiguration.DD_AGENT_PORT, null)
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_PORT, null)
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_URL, null)
                .execute(DatadogGlobalConfiguration::new);

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogAgentConfiguration);
        DatadogAgentConfiguration datadogClientConfiguration = (DatadogAgentConfiguration) configuration.getDatadogClientConfiguration();

        assertEquals("my-target-host", datadogClientConfiguration.getAgentHost());
        assertEquals((Integer) 123, datadogClientConfiguration.getAgentPort());
        assertEquals((Integer) 456, datadogClientConfiguration.getAgentTraceCollectionPort());
        assertEquals((Integer) 789, datadogClientConfiguration.getAgentLogCollectionPort());
    }

    @Test
    public void testLegacyAgentConfiguration() throws Exception {
        DatadogGlobalConfiguration configuration = SystemLambda
                .withEnvironmentVariable(DatadogGlobalConfiguration.REPORT_WITH_PROPERTY, "DSD")
                .and(DatadogAgentConfiguration.TARGET_HOST_PROPERTY, null)
                .and(DatadogAgentConfiguration.TARGET_PORT_PROPERTY, null)
                .and(DatadogAgentConfiguration.TARGET_TRACE_COLLECTION_PORT_PROPERTY, "456")
                .and(DatadogAgentConfiguration.TARGET_LOG_COLLECTION_PORT_PROPERTY, "789")
                .and(DatadogAgentConfiguration.DD_AGENT_HOST, "my-target-host")
                .and(DatadogAgentConfiguration.DD_AGENT_PORT, "123")
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_PORT, null)
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_URL, null)
                .execute(DatadogGlobalConfiguration::new);

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogAgentConfiguration);
        DatadogAgentConfiguration datadogClientConfiguration = (DatadogAgentConfiguration) configuration.getDatadogClientConfiguration();

        assertEquals("my-target-host", datadogClientConfiguration.getAgentHost());
        assertEquals((Integer) 123, datadogClientConfiguration.getAgentPort());
        assertEquals((Integer) 456, datadogClientConfiguration.getAgentTraceCollectionPort());
        assertEquals((Integer) 789, datadogClientConfiguration.getAgentLogCollectionPort());
    }

    @Test
    public void testLegacyAgentPortConfiguration() throws Exception {
        DatadogGlobalConfiguration configuration = SystemLambda
                .withEnvironmentVariable(DatadogGlobalConfiguration.REPORT_WITH_PROPERTY, "DSD")
                .and(DatadogAgentConfiguration.TARGET_HOST_PROPERTY, null)
                .and(DatadogAgentConfiguration.TARGET_PORT_PROPERTY, "123")
                .and(DatadogAgentConfiguration.TARGET_TRACE_COLLECTION_PORT_PROPERTY, null)
                .and(DatadogAgentConfiguration.TARGET_LOG_COLLECTION_PORT_PROPERTY, "789")
                .and(DatadogAgentConfiguration.DD_AGENT_HOST, "my-target-host")
                .and(DatadogAgentConfiguration.DD_AGENT_PORT, null)
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_PORT, "456")
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_URL, null)
                .execute(DatadogGlobalConfiguration::new);

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogAgentConfiguration);
        DatadogAgentConfiguration datadogClientConfiguration = (DatadogAgentConfiguration) configuration.getDatadogClientConfiguration();

        assertEquals("my-target-host", datadogClientConfiguration.getAgentHost());
        assertEquals((Integer) 123, datadogClientConfiguration.getAgentPort());
        assertEquals((Integer) 456, datadogClientConfiguration.getAgentTraceCollectionPort());
        assertEquals((Integer) 789, datadogClientConfiguration.getAgentLogCollectionPort());
    }

    @Test
    public void testLegacyAgentUrlConfiguration() throws Exception {
        DatadogGlobalConfiguration configuration = SystemLambda
                .withEnvironmentVariable(DatadogGlobalConfiguration.REPORT_WITH_PROPERTY, "DSD")
                .and(DatadogAgentConfiguration.TARGET_HOST_PROPERTY, null)
                .and(DatadogAgentConfiguration.TARGET_PORT_PROPERTY, "123")
                .and(DatadogAgentConfiguration.TARGET_TRACE_COLLECTION_PORT_PROPERTY, null)
                .and(DatadogAgentConfiguration.TARGET_LOG_COLLECTION_PORT_PROPERTY, "789")
                .and(DatadogAgentConfiguration.DD_AGENT_HOST, null)
                .and(DatadogAgentConfiguration.DD_AGENT_PORT, null)
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_PORT, null)
                .and(DatadogAgentConfiguration.DD_TRACE_AGENT_URL, "http://my-target-host:456")
                .execute(DatadogGlobalConfiguration::new);

        assertTrue(configuration.getDatadogClientConfiguration() instanceof DatadogAgentConfiguration);
        DatadogAgentConfiguration datadogClientConfiguration = (DatadogAgentConfiguration) configuration.getDatadogClientConfiguration();

        assertEquals("my-target-host", datadogClientConfiguration.getAgentHost());
        assertEquals((Integer) 123, datadogClientConfiguration.getAgentPort());
        assertEquals((Integer) 456, datadogClientConfiguration.getAgentTraceCollectionPort());
        assertEquals((Integer) 789, datadogClientConfiguration.getAgentLogCollectionPort());
    }

}
