package org.datadog.jenkins.plugins.datadog.configuration.api.intake;

import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.DEFAULT_API_URL_VALUE;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.DEFAULT_LOG_INTAKE_URL_VALUE;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.DEFAULT_WEBHOOK_INTAKE_URL_VALUE;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.TARGET_API_URL_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.TARGET_LOG_INTAKE_URL_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls.TARGET_WEBHOOK_INTAKE_URL_PROPERTY;
import static org.junit.Assert.assertEquals;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import org.junit.Test;

public class DatadogIntakeUrlsTest {

    @Test
    public void testGetSiteName() {
        for (DatadogSite site : DatadogSite.values()) {
            DatadogIntakeUrls intakeUrls = new DatadogIntakeUrls(site.getApiUrl(), site.getLogsUrl(), site.getWebhooksUrl());
            assertEquals(site.getSiteName(), intakeUrls.getSiteName());
        }
    }

    @Test
    public void testGetDefaultApiUrl() throws Exception {
        // check default value
        assertEquals(DEFAULT_API_URL_VALUE, DatadogIntakeUrls.DatadogIntakeUrlsDescriptor.getDefaultApiUrl());
        // check environment property overrides
        assertEquals("overridden-url", SystemLambda
                .withEnvironmentVariable(TARGET_API_URL_PROPERTY, "overridden-url")
                .execute(DatadogIntakeUrls.DatadogIntakeUrlsDescriptor::getDefaultApiUrl));
    }

    @Test
    public void testGetDefaultLogIntakeUrl() throws Exception {
        // check default value
        assertEquals(DEFAULT_LOG_INTAKE_URL_VALUE, DatadogIntakeUrls.DatadogIntakeUrlsDescriptor.getDefaultLogsUrl());
        // check environment property overrides
        assertEquals("overridden-url", SystemLambda
                .withEnvironmentVariable(TARGET_LOG_INTAKE_URL_PROPERTY, "overridden-url")
                .execute(DatadogIntakeUrls.DatadogIntakeUrlsDescriptor::getDefaultLogsUrl));
    }

    @Test
    public void testGetDefaultWebhookIntakeUrl() throws Exception {
        // check default value
        assertEquals(DEFAULT_WEBHOOK_INTAKE_URL_VALUE, DatadogIntakeUrls.DatadogIntakeUrlsDescriptor.getDefaultWebhooksUrl());
        // check environment property overrides
        assertEquals("overridden-url", SystemLambda
                .withEnvironmentVariable(TARGET_WEBHOOK_INTAKE_URL_PROPERTY, "overridden-url")
                .execute(DatadogIntakeUrls.DatadogIntakeUrlsDescriptor::getDefaultWebhooksUrl));
    }
}