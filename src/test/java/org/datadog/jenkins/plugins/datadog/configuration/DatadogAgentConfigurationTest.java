package org.datadog.jenkins.plugins.datadog.configuration;

import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DD_AGENT_HOST;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DD_AGENT_PORT;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DD_TRACE_AGENT_PORT;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DD_TRACE_AGENT_URL;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DEFAULT_AGENT_HOST_VALUE;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DEFAULT_AGENT_PORT_VALUE;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DEFAULT_LOG_COLLECTION_PORT_VALUE;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DEFAULT_TRACE_COLLECTION_PORT_VALUE;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.TARGET_HOST_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.TARGET_LOG_COLLECTION_PORT_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.TARGET_PORT_PROPERTY;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.TARGET_TRACE_COLLECTION_PORT_PROPERTY;
import static org.junit.Assert.assertEquals;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import org.junit.Test;

public class DatadogAgentConfigurationTest {

    @Test
    public void testGetDefaultAgentHost() throws Exception {
        // check default value
        assertEquals(DEFAULT_AGENT_HOST_VALUE, DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentHost());
        // check environment property overrides
        assertEquals("target-host", SystemLambda
                .withEnvironmentVariable(TARGET_HOST_PROPERTY, "target-host")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentHost));
        assertEquals("agent-host", SystemLambda
                .withEnvironmentVariable(DD_TRACE_AGENT_URL, "http://agent-host:1234")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentHost));
        assertEquals("dd-agent-host", SystemLambda
                .withEnvironmentVariable(DD_AGENT_HOST, "dd-agent-host")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentHost));
        // check environment property priorities
        assertEquals("target-host", SystemLambda
                .withEnvironmentVariable(TARGET_HOST_PROPERTY, "target-host")
                .and(DD_TRACE_AGENT_URL, "http://agent-host:1234")
                .and(DD_AGENT_HOST, "dd-agent-host")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentHost));
        assertEquals("agent-host", SystemLambda
                .withEnvironmentVariable(DD_TRACE_AGENT_URL, "http://agent-host:1234")
                .and(DD_AGENT_HOST, "dd-agent-host")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentHost));
    }

    @Test
    public void testGetDefaultAgentPort() throws Exception {
        // check default value
        assertEquals(DEFAULT_AGENT_PORT_VALUE, DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentPort());
        // check environment property overrides
        assertEquals((Integer) 1234, SystemLambda
                .withEnvironmentVariable(TARGET_PORT_PROPERTY, "1234")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentPort));
        assertEquals((Integer) 5678, SystemLambda
                .withEnvironmentVariable(DD_AGENT_PORT, "5678")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentPort));
        // check environment property priorities
        assertEquals((Integer) 1234, SystemLambda
                .withEnvironmentVariable(TARGET_PORT_PROPERTY, "1234")
                .and(DD_AGENT_PORT, "5678")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentPort));
    }

    @Test
    public void testGetDefaultAgentLogCollectionPort() throws Exception {
        // check default value
        assertEquals(DEFAULT_LOG_COLLECTION_PORT_VALUE, DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentLogCollectionPort());
        // check environment property overrides
        assertEquals((Integer) 1234, SystemLambda
                .withEnvironmentVariable(TARGET_LOG_COLLECTION_PORT_PROPERTY, "1234")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentLogCollectionPort));
    }

    @Test
    public void testGetDefaultAgentTraceCollectionPort() throws Exception {
        // check default value
        assertEquals(DEFAULT_TRACE_COLLECTION_PORT_VALUE, DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentTraceCollectionPort());
        // check environment property overrides
        assertEquals((Integer) 1234, SystemLambda
                .withEnvironmentVariable(TARGET_TRACE_COLLECTION_PORT_PROPERTY, "1234")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentTraceCollectionPort));
        assertEquals((Integer) 2468, SystemLambda
                .withEnvironmentVariable(DD_TRACE_AGENT_URL, "http://agent-host:2468")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentTraceCollectionPort));
        assertEquals((Integer) 5678, SystemLambda
                .withEnvironmentVariable(DD_TRACE_AGENT_PORT, "5678")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentTraceCollectionPort));
        // check environment property priorities
        assertEquals((Integer) 1234, SystemLambda
                .withEnvironmentVariable(TARGET_TRACE_COLLECTION_PORT_PROPERTY, "1234")
                .and(DD_TRACE_AGENT_URL, "http://agent-host:2468")
                .and(DD_TRACE_AGENT_PORT, "5678")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentTraceCollectionPort));
        assertEquals((Integer) 2468, SystemLambda
                .withEnvironmentVariable(DD_TRACE_AGENT_URL, "http://agent-host:2468")
                .and(DD_TRACE_AGENT_PORT, "5678")
                .execute(DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor::getDefaultAgentTraceCollectionPort));
    }
}