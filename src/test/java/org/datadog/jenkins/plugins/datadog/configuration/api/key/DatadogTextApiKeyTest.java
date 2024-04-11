package org.datadog.jenkins.plugins.datadog.configuration.api.key;

import static org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogTextApiKey.TARGET_API_KEY_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import hudson.util.Secret;
import org.junit.Test;

public class DatadogTextApiKeyTest {

    @Test
    public void testGetDefaultApiKey() throws Exception {
        // check default value
        assertNull(DatadogTextApiKey.DatadogTextApiKeyDescriptor.getDefaultKey());
        // check environment property overrides
        assertEquals(Secret.fromString("an-api-key"), SystemLambda
                .withEnvironmentVariable(TARGET_API_KEY_PROPERTY, "an-api-key")
                .execute(DatadogTextApiKey.DatadogTextApiKeyDescriptor::getDefaultKey));
    }
}
