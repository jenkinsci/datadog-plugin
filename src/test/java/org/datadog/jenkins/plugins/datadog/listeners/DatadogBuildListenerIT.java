package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;

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
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

public class DatadogBuildListenerIT {

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private DatadogClientStub clientStub;

    @BeforeClass
    public static void setup() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setCollectBuildTraces(true);
    }

    @Before
    public void beforeEach() {
        clientStub = new DatadogClientStub();
        ClientFactory.setTestClient(clientStub);
        clientStub.tracerWriter.start();
    }

    @Test
    public void testTraces() throws Exception {
        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccess");
        project.scheduleBuild2(0).get();

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(1);
        assertEquals(1, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final String buildPrefix = BuildPipelineNode.NodeType.PIPELINE.getNormalizedName();
        final DDSpan buildSpan = buildTrace.get(0);
        assertEquals("jenkins.build", buildSpan.getOperationName());
        assertEquals("jenkins", buildSpan.getServiceName());
        assertEquals("buildIntegrationSuccess", buildSpan.getResourceName());
        assertEquals("ci", buildSpan.getType());
        assertEquals("buildIntegrationSuccess", buildSpan.getTag(buildPrefix + CITags._NAME));
        assertEquals("SUCCESS", buildSpan.getTag(buildPrefix + CITags._RESULT));
        assertEquals("1", buildSpan.getTag(buildPrefix + CITags._ID));
        assertEquals("SUCCESS", buildSpan.getTag(CITags.JENKINS_RESULT));
        assertEquals("jenkins", buildSpan.getTag(CITags.CI_PROVIDER));
        assertEquals("1", buildSpan.getTag(buildPrefix + CITags._NUMBER));
        assertEquals("anonymous", buildSpan.getTag(CITags.USER_NAME));
        assertEquals("jenkins-buildIntegrationSuccess-1", buildSpan.getTag(CITags.JENKINS_TAG));
    }
}
