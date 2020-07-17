package org.datadog.jenkins.plugins.datadog.steps;

import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.model.labels.LabelAtom;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.clients.DatadogMetric;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
        EnvVars env = new EnvVars();
        env.put("DD_TEST", "bar");
        j.createOnlineSlave(new LabelAtom("test"), env);
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
        Assert.assertTrue(stubClient.logLines.contains("foo"));
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
        List<DatadogMetric> metrics = stubClient.metrics;
        String hostname = DatadogUtilities.getHostname(null);
        String[] expectedTags = new String[]{
                "jenkins_url:" + DatadogUtilities.getJenkinsUrl(),
                "user_id:anonymous",
                "job:testMetricTags",
                "result:SUCCESS",
                "foo:bar"
        };
        stubClient.assertMetric("jenkins.job.duration", hostname, expectedTags);
    }
}
