package org.datadog.jenkins.plugins.datadog.util.config;

import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.DEFAULT_API_URL_VALUE;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.DEFAULT_LOG_INTAKE_URL_VALUE;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.DEFAULT_WEBHOOK_INTAKE_URL_VALUE;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.TARGET_API_KEY_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.TARGET_API_URL_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.TARGET_LOG_INTAKE_URL_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.util.config.DatadogApiConfiguration.TARGET_WEBHOOK_INTAKE_URL_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.github.stefanbirkner.systemlambda.SystemLambda;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DatadogApiConfigurationTest {

    @ClassRule
    public static JenkinsRule jenkinsRule;

    static {
        jenkinsRule = new JenkinsRule();
        jenkinsRule.timeout = 300; // default value of 180 is too small for all the test cases in this class
    }

    @Test
    public void testCanGetCredentialFromId() throws Exception {
        CredentialsStore credentialsStore = CredentialsProvider.lookupStores(jenkinsRule).iterator().next();

        StringCredentials credential1 = new StringCredentialsImpl(CredentialsScope.SYSTEM, "string-cred-id", "description", Secret.fromString("api-key"));
        credentialsStore.addCredentials(Domain.global(), credential1);
        assertEquals(credential1, DatadogApiConfiguration.getCredentialFromId("string-cred-id"));

        StringCredentials credential2 = new StringCredentialsImpl(CredentialsScope.SYSTEM, "string-cred-id2", "description", Secret.fromString("api-key"));
        credentialsStore.addCredentials(Domain.global(), credential2);
        assertEquals(credential1, DatadogApiConfiguration.getCredentialFromId("string-cred-id"));
        assertEquals(credential2, DatadogApiConfiguration.getCredentialFromId("string-cred-id2"));

        assertNull(DatadogApiConfiguration.getCredentialFromId("string-cred-id-fake"));
    }

    @Test
    public void testGetCredential() throws Exception {
        CredentialsStore credentialsStore = CredentialsProvider.lookupStores(jenkinsRule).iterator().next();

        StringCredentials credential1 = new StringCredentialsImpl(CredentialsScope.SYSTEM, "string-cred-id", "description", Secret.fromString("api-key"));
        credentialsStore.addCredentials(Domain.global(), credential1);

        StringCredentials credential2 = new StringCredentialsImpl(CredentialsScope.SYSTEM, "string-cred-id2", "description", Secret.fromString("api-key-2"));
        credentialsStore.addCredentials(Domain.global(), credential2);

        Secret apiTestSecret = Secret.fromString("api-test");

        assertEquals("api-key", DatadogApiConfiguration.getCredential("string-cred-id", apiTestSecret).getPlainText());
        assertEquals("api-key-2", DatadogApiConfiguration.getCredential("string-cred-id2", apiTestSecret).getPlainText());

        assertEquals("api-test", DatadogApiConfiguration.getCredential("", apiTestSecret).getPlainText());
        assertEquals("api-test", DatadogApiConfiguration.getCredential(null, apiTestSecret).getPlainText());
        assertEquals("", DatadogApiConfiguration.getCredential(null, Secret.fromString("")).getPlainText());

        assertEquals("", DatadogApiConfiguration.getCredential("", Secret.fromString(null)).getPlainText());
    }

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

    @Test
    public void testGetDefaultApiKey() throws Exception {
        // check default value
        assertNull(DatadogApiConfiguration.DatadogApiConfigurationDescriptor.getDefaultApiKey());
        // check environment property overrides
        assertEquals(Secret.fromString("an-api-key"), SystemLambda
                .withEnvironmentVariable(TARGET_API_KEY_PROPERTY, "an-api-key")
                .execute(DatadogApiConfiguration.DatadogApiConfigurationDescriptor::getDefaultApiKey));
    }
}