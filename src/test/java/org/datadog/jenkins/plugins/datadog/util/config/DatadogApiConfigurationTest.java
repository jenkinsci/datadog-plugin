package org.datadog.jenkins.plugins.datadog.util.config;

import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.DEFAULT_API_URL_VALUE;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.DEFAULT_LOG_INTAKE_URL_VALUE;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.DEFAULT_WEBHOOK_INTAKE_URL_VALUE;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.TARGET_API_URL_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.TARGET_LOG_INTAKE_URL_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.TARGET_WEBHOOK_INTAKE_URL_PROPERTY;
import static org.junit.Assert.assertEquals;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import org.junit.Test;

public class DatadogApiConfigurationTest {

    @Test
    public void testGetDefaultApiUrl() throws Exception {
        // check default value
        assertEquals(DEFAULT_API_URL_VALUE, DatadogApiConfiguration.DatadogApiConfigurationDescriptor.getDefaultApiUrl());
        // check environment property overrides
        assertEquals("overridden-url", SystemLambda
                .withEnvironmentVariable(TARGET_API_URL_PROPERTY, "overridden-url")
                .execute(DatadogApiConfiguration.DatadogApiConfigurationDescriptor::getDefaultApiUrl));
    }

    @Test
    public void testGetDefaultLogIntakeUrl() throws Exception {
        // check default value
        assertEquals(DEFAULT_LOG_INTAKE_URL_VALUE, DatadogApiConfiguration.DatadogApiConfigurationDescriptor.getDefaultLogIntakeUrl());
        // check environment property overrides
        assertEquals("overridden-url", SystemLambda
                .withEnvironmentVariable(TARGET_LOG_INTAKE_URL_PROPERTY, "overridden-url")
                .execute(DatadogApiConfiguration.DatadogApiConfigurationDescriptor::getDefaultLogIntakeUrl));
    }

    @Test
    public void testGetDefaultWebhookIntakeUrl() throws Exception {
        // check default value
        assertEquals(DEFAULT_WEBHOOK_INTAKE_URL_VALUE, DatadogApiConfiguration.DatadogApiConfigurationDescriptor.getDefaultWebhookIntakeUrl());
        // check environment property overrides
        assertEquals("overridden-url", SystemLambda
                .withEnvironmentVariable(TARGET_WEBHOOK_INTAKE_URL_PROPERTY, "overridden-url")
                .execute(DatadogApiConfiguration.DatadogApiConfigurationDescriptor::getDefaultWebhookIntakeUrl));
    }
}