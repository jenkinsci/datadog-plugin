package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import datadog.trace.common.writer.ListWriter;
import datadog.trace.core.DDSpan;
import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public class DatadogBuildListenerIT {

    private static final String SAMPLE_SERVICE_NAME = "sampleServiceName";

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private DatadogClientStub clientStub;

    @Before
    public void beforeEach() throws IOException {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setCollectBuildTraces(true);
        cfg.setTraceServiceName(SAMPLE_SERVICE_NAME);

        clientStub = new DatadogClientStub();
        ClientFactory.setTestClient(clientStub);
        clientStub.tracerWriter.start();

        Jenkins jenkins = jenkinsRule.jenkins;
        jenkins.getGlobalNodeProperties().remove(EnvironmentVariablesNodeProperty.class);
    }

    @Test
    public void testTraces() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        final EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put("GIT_BRANCH", "master");
        env.put("GIT_COMMIT", "401d997a6eede777602669ccaec059755c98161f");
        env.put("GIT_URL", "https://github.com/johndoe/foobar.git");
        jenkins.getGlobalNodeProperties().add(prop);

        final FreeStyleProject project = jenkinsRule.createFreeStyleProject("buildIntegrationSuccess");
        final URL gitZip = getClass().getClassLoader().getResource("org/datadog/jenkins/plugins/datadog/listeners/git/gitFolder.zip");
        if(gitZip != null) {
            project.setScm(new ExtractResourceSCM(gitZip));
        }
        project.scheduleBuild2(0).get();

        final ListWriter tracerWriter = clientStub.tracerWriter();
        tracerWriter.waitForTraces(1);
        assertEquals(1, tracerWriter.size());

        final List<DDSpan> buildTrace = tracerWriter.get(0);
        assertEquals(1, buildTrace.size());

        final String buildPrefix = BuildPipelineNode.NodeType.PIPELINE.getTagName();
        final DDSpan buildSpan = buildTrace.get(0);
        assertGitVariables(buildSpan);
        assertEquals(BuildPipelineNode.NodeType.PIPELINE.getBuildLevel(), buildSpan.getTag(CITags._DD_CI_BUILD_LEVEL));
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


    private void assertGitVariables(DDSpan span) {
        assertEquals("Initial commit\n", span.getTag(CITags.GIT_COMMIT_MESSAGE));
        assertEquals("John Doe", span.getTag(CITags.GIT_COMMIT_AUTHOR_NAME));
        assertEquals("john@doe.com", span.getTag(CITags.GIT_COMMIT_AUTHOR_EMAIL));
        assertNotNull(span.getTag(CITags.GIT_COMMIT_AUTHOR_DATE));
        assertEquals("John Doe", span.getTag(CITags.GIT_COMMIT_COMMITTER_NAME));
        assertEquals("john@doe.com", span.getTag(CITags.GIT_COMMIT_COMMITTER_EMAIL));
        assertNotNull(span.getTag(CITags.GIT_COMMIT_COMMITTER_DATE));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", span.getTag(CITags.GIT_COMMIT__SHA));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", span.getTag(CITags.GIT_COMMIT_SHA));
        assertEquals("master", span.getTag(CITags.GIT_BRANCH));
        assertEquals("https://github.com/johndoe/foobar.git", span.getTag(CITags.GIT_REPOSITORY_URL));
    }
}
