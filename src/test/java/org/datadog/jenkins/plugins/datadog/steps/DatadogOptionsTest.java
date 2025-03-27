package org.datadog.jenkins.plugins.datadog.steps;

import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.model.labels.LabelAtom;
import java.util.List;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.jvnet.hudson.test.JenkinsRule;

public class DatadogOptionsTest {

    private static final String TEST_NODE_HOSTNAME = "test-node-hostname";

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    /**
     * CI provider sets these environment variables.
     * They have to be cleared, otherwise they interfere with the tests.
     */
    @ClassRule
    public static final EnvironmentVariables environmentVariables = new EnvironmentVariables()
        .set(GitUtils.GIT_BRANCH_ALT, null)
        .set(GitUtils.CHANGE_BRANCH, null);

    private static DatadogClientStub stubClient = new DatadogClientStub();

    @BeforeClass
    public static void setup() throws Exception {
        ClientHolder.setClient(stubClient);
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        ExtensionList.clearLegacyInstances();
        cfg.setCollectBuildLogs(false);

        EnvVars testNodeEnvVars = new EnvVars();
        testNodeEnvVars.put("HOSTNAME", TEST_NODE_HOSTNAME);
        j.createOnlineSlave(new LabelAtom("test"), testNodeEnvVars);
    }

    @Test
    public void testLogCollection() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "testLogCollection");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("logCollectedInOptions.txt"),
                "UTF-8"
        );
        p.setDefinition(new CpsFlowDefinition(definition, true));
        p.scheduleBuild2(0).get();
        assertLogs("foo", false);
    }

    @Test
    public void testMetricTags() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "testMetricTags");
        String definition = IOUtils.toString(
                this.getClass().getResourceAsStream("pipelineMetricTags.txt"),
                "UTF-8"
        );
        p.setDefinition(new CpsFlowDefinition(definition, true));
        p.scheduleBuild2(0).get();
        String[] expectedTags = new String[]{
                "node:slave0",
                "jenkins_url:" + DatadogUtilities.getJenkinsUrl(),
                "user_id:anonymous",
                "job:testMetricTags",
                "result:SUCCESS",
                "foo:bar",
                "bar:foo"
        };
        stubClient.assertMetric("jenkins.job.duration", TEST_NODE_HOSTNAME, expectedTags);
    }

    private void assertLogs(final String expectedMessage, final boolean checkTraces) {
        boolean hasExpectedMessage = false;
        List<JSONObject> logLines = stubClient.getLogLines();
        for(final JSONObject logLine : logLines) {
            if(checkTraces) {
                Assert.assertNotNull(logLine.get("dd.trace_id"));
                Assert.assertNotNull(logLine.get("dd.span_id"));
            }

            if(!hasExpectedMessage) {
                hasExpectedMessage = expectedMessage.equals(logLine.get("message"));
            }
        }
        Assert.assertTrue("loglines does not contain '"+expectedMessage+"' message.", hasExpectedMessage);
    }
}
