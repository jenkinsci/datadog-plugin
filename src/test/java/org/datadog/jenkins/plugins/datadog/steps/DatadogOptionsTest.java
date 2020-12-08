package org.datadog.jenkins.plugins.datadog.steps;

import hudson.ExtensionList;
import hudson.model.labels.LabelAtom;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

public class DatadogOptionsTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    private static DatadogClientStub stubClient = new DatadogClientStub();

    @BeforeClass
    public static void setup() throws Exception {
        ClientFactory.setTestClient(stubClient);
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        ExtensionList.clearLegacyInstances();
        cfg.setCollectBuildLogs(false);
        j.createOnlineSlave(new LabelAtom("test"));
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
        String hostname = DatadogUtilities.getHostname(null);
        String[] expectedTags = new String[]{
                "jenkins_url:" + DatadogUtilities.getJenkinsUrl(),
                "user_id:anonymous",
                "job:testMetricTags",
                "result:SUCCESS",
                "foo:bar",
                "bar:foo"
        };
        stubClient.assertMetric("jenkins.job.duration", hostname, expectedTags);
    }

    private void assertLogs(final String expectedMessage, final boolean checkTraces) {
        boolean hasExpectedMessage = false;
        List<JSONObject> logLines = stubClient.logLines;
        for(final JSONObject logLine : logLines) {
            System.out.println("LOG: " + logLine.toString());
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
