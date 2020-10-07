package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import datadog.trace.common.writer.ListWriter;
import datadog.trace.core.DDSpan;
import hudson.model.FreeStyleProject;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

import java.util.List;

public class DatadogBuildListenerIT {

    private static final String SAMPLE_SERVICE_NAME = "sampleServiceName";

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private DatadogClientStub clientStub;

    @Before
    public void beforeEach() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setCollectBuildTraces(true);
        cfg.setTraceServiceName(SAMPLE_SERVICE_NAME);

        clientStub = new DatadogClientStub();
        ClientFactory.setTestClient(clientStub);
        clientStub.tracerWriter.start();
    }

    @Test
    public void testTraces() throws Exception {
        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccess");
        project.setScm(new SingleFileSCM("greeting.txt", "hello"));
        project.scheduleBuild2(0).get();

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(1);
        assertEquals(1, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final String buildPrefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();
        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("jenkins.build", buildSpan.getOperationName());
        assertEquals(SAMPLE_SERVICE_NAME, buildSpan.getServiceName());
        assertEquals("buildIntegrationSuccess", buildSpan.getResourceName());
        assertEquals("ci", buildSpan.getType());
        assertEquals("jenkins", buildSpan.getTag(CITags.CI_PROVIDER_NAME));
        assertEquals("anonymous", buildSpan.getTag(CITags.USER_NAME));
        assertEquals("jenkins-buildIntegrationSuccess-1", buildSpan.getTag(buildPrefix + CITags._ID));
        assertNotNull(buildSpan.getTag(buildPrefix + CITags._QUEUE_TIME));
        assertEquals("buildIntegrationSuccess", buildSpan.getTag(buildPrefix + CITags._NAME));
        assertEquals("1", buildSpan.getTag(buildPrefix + CITags._NUMBER));
        assertNotNull(buildSpan.getTag(buildPrefix + CITags._URL));
        assertNotNull(buildSpan.getTag(CITags.WORKSPACE_PATH));
        assertEquals("success", buildSpan.getTag(buildPrefix + CITags._RESULT));
        assertNotNull(buildSpan.getTag(CITags.NODE_NAME));
        assertNotNull(buildSpan.getTag(CITags._DD_HOSTNAME));
        assertEquals("success", buildSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins-buildIntegrationSuccess-1", buildSpan.getTag(CITags.JENKINS_TAG));
    }

    @Test
    public void testTracesDisabled() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setCollectBuildTraces(false);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccess-notraces");
        project.scheduleBuild2(0).get();

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(0);
        assertEquals(0, tracerWriter.size());
    }
}
