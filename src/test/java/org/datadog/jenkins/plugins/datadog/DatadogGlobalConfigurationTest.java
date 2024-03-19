package org.datadog.jenkins.plugins.datadog;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.jvnet.hudson.test.JenkinsRule;

import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;

import java.io.IOException;

public class DatadogGlobalConfigurationTest {

    @ClassRule
    public static JenkinsRule jenkinsRule;

    static {
        jenkinsRule = new JenkinsRule();
        jenkinsRule.timeout = 600; // default value of 180 is too small for all the test cases in this class
    }

    @Rule public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("test-config.yml")
    public void TestConfigurationAsCodeCompatibility() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        Assert.assertTrue(cfg.isEmitConfigChangeEvents());
    }

    @Test
    public void testCanGetCredentialFromId() throws IOException {
        CredentialsStore credentialsStore = CredentialsProvider.lookupStores(jenkinsRule).iterator().next();

        StringCredentials credential1 = new StringCredentialsImpl(CredentialsScope.SYSTEM, "string-cred-id", "description", Secret.fromString("api-key"));
        credentialsStore.addCredentials(Domain.global(), credential1);
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        Assert.assertTrue(cfg.getCredentialFromId("string-cred-id").equals(credential1));

        StringCredentials credential2 = new StringCredentialsImpl(CredentialsScope.SYSTEM, "string-cred-id2", "description", Secret.fromString("api-key"));
        credentialsStore.addCredentials(Domain.global(), credential2);
        Assert.assertTrue(cfg.getCredentialFromId("string-cred-id").equals(credential1));
        Assert.assertTrue(cfg.getCredentialFromId("string-cred-id2").equals(credential2));

        Assert.assertNull(cfg.getCredentialFromId("string-cred-id-fake"));

    }

    @Test
    public void testFindSecret() throws IOException {
        CredentialsStore credentialsStore = CredentialsProvider.lookupStores(jenkinsRule).iterator().next();

        StringCredentials credential1 = new StringCredentialsImpl(CredentialsScope.SYSTEM, "string-cred-id", "description", Secret.fromString("api-key"));
        credentialsStore.addCredentials(Domain.global(), credential1);

        StringCredentials credential2 = new StringCredentialsImpl(CredentialsScope.SYSTEM, "string-cred-id2", "description", Secret.fromString("api-key-2"));
        credentialsStore.addCredentials(Domain.global(), credential2);

        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        Assert.assertTrue(cfg.findSecret("api-test", "string-cred-id").getPlainText().equals("api-key"));
        Assert.assertTrue(cfg.findSecret("api-test", "string-cred-id2").getPlainText().equals("api-key-2"));

        Assert.assertTrue(cfg.findSecret("api-test", "").getPlainText().equals("api-test"));
        Assert.assertTrue(cfg.findSecret("api-test", null).getPlainText().equals("api-test"));
        Assert.assertTrue(cfg.findSecret("", null).getPlainText().equals(""));

        Assert.assertTrue(cfg.findSecret(null, "").getPlainText().equals(""));
    }
}

