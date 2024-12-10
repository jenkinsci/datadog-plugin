package org.datadog.jenkins.plugins.datadog.configuration.api.key;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DatadogCredentialsApiKeyTest {

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
        assertEquals(credential1, DatadogCredentialsApiKey.getCredentialFromId("string-cred-id"));

        StringCredentials credential2 = new StringCredentialsImpl(CredentialsScope.SYSTEM, "string-cred-id2", "description", Secret.fromString("api-key"));
        credentialsStore.addCredentials(Domain.global(), credential2);
        assertEquals(credential1, DatadogCredentialsApiKey.getCredentialFromId("string-cred-id"));
        assertEquals(credential2, DatadogCredentialsApiKey.getCredentialFromId("string-cred-id2"));

        assertNull(DatadogCredentialsApiKey.getCredentialFromId("string-cred-id-fake"));
    }

}
