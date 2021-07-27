package org.datadog.jenkins.plugins.datadog.util.config;

import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.DD_AGENT_HOST;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.DD_AGENT_PORT;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.DD_TRACE_AGENT_PORT;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.DD_TRACE_AGENT_URL;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.TARGET_HOST_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.TARGET_PORT_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration.TARGET_TRACE_COLLECTION_PORT_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class DatadogAgentConfigurationTest {

    @Test
    public void testResolveEmptyConfig() {
        final DatadogAgentConfiguration config = DatadogAgentConfiguration.resolve(new HashMap<>());
        assertNull(config.getHost());
        assertNull(config.getPort());
        assertNull(config.getTracesPort());
    }

    @Test
    public void testResolveGenericEnvVars() {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(DD_AGENT_HOST, "localhost");
        envVars.put(DD_AGENT_PORT, "8125");
        envVars.put(DD_TRACE_AGENT_PORT, "8126");
        final DatadogAgentConfiguration config = DatadogAgentConfiguration.resolve(envVars);
        assertEquals("localhost", config.getHost());
        assertEquals(Integer.valueOf(8125), config.getPort());
        assertEquals(Integer.valueOf(8126), config.getTracesPort());
    }

    @Test
    public void testResolveUsingTraceUrl() {
        final Map<String, String> usingTraceUrl = new HashMap<>();
        usingTraceUrl.put(DD_TRACE_AGENT_URL, "http://localhost:8126");
        usingTraceUrl.put(DD_AGENT_PORT, "8125");

        final DatadogAgentConfiguration config = DatadogAgentConfiguration.resolve(usingTraceUrl);
        assertEquals("localhost", config.getHost());
        assertEquals(Integer.valueOf(8125), config.getPort());
        assertEquals(Integer.valueOf(8126), config.getTracesPort());
    }

    @Test
    public void testResolveSpecificEnvVars() {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(TARGET_HOST_PROPERTY, "localhost");
        envVars.put(TARGET_PORT_PROPERTY, "8125");
        envVars.put(TARGET_TRACE_COLLECTION_PORT_PROPERTY, "8126");

        final DatadogAgentConfiguration config = DatadogAgentConfiguration.resolve(envVars);
        assertEquals("localhost", config.getHost());
        assertEquals(Integer.valueOf(8125), config.getPort());
        assertEquals(Integer.valueOf(8126), config.getTracesPort());
    }

    @Test
    public void testPrevalenceSpecificEnvVars() {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(DD_AGENT_HOST, "some-host");
        envVars.put(DD_AGENT_PORT, "1111");
        envVars.put(DD_TRACE_AGENT_PORT, "1112");
        envVars.put(TARGET_HOST_PROPERTY, "localhost");
        envVars.put(TARGET_PORT_PROPERTY, "8125");
        envVars.put(TARGET_TRACE_COLLECTION_PORT_PROPERTY, "8126");

        final DatadogAgentConfiguration config = DatadogAgentConfiguration.resolve(envVars);
        assertEquals("localhost", config.getHost());
        assertEquals(Integer.valueOf(8125), config.getPort());
        assertEquals(Integer.valueOf(8126), config.getTracesPort());
    }

    @Test
    public void testPrevalenceTraceUrlEnvVars() {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(DD_TRACE_AGENT_URL, "http://localhost:8126");
        envVars.put(DD_AGENT_PORT, "8125");
        envVars.put(DD_TRACE_AGENT_PORT, "1112");

        final DatadogAgentConfiguration config = DatadogAgentConfiguration.resolve(envVars);
        assertEquals("localhost", config.getHost());
        assertEquals(Integer.valueOf(8125), config.getPort());
        assertEquals(Integer.valueOf(8126), config.getTracesPort());
    }

}