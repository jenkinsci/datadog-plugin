package org.datadog.jenkins.plugins.datadog.logs;

import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;

import java.util.List;
import jenkins.model.Jenkins.MasterComputer;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class LogCollectionTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    private static DatadogClientStub stubClient = new DatadogClientStub();

    @BeforeClass
    public static void staticSetup() throws Exception {
        ClientHolder.setClient(stubClient);
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        ExtensionList.clearLegacyInstances();
        cfg.setCollectBuildLogs(true);
        j.createOnlineSlave(new LabelAtom("test"));
    }

    @Before
    public void setup() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(false);
    }


    @Test
    public void testFreeStyleProject() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        MasterComputer master = null;
        for(Computer c: j.jenkins.getComputers()){
            if(c instanceof MasterComputer){
                master = (MasterComputer)c;
                break;
            }
        }
        if(master == null){
            Assert.fail("Unable to find the master computer.");
        }

        if(master.isUnix()){
            p.getBuildersList().add(new Shell("echo foo"));
        } else {
            p.getBuildersList().add(new BatchFile("echo foo"));
        }
        p.scheduleBuild2(0).get();
        assertLogs("foo", false);
    }

    @Test
    public void testFreeStyleProjectWithTraces() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);

        FreeStyleProject p = j.createFreeStyleProject();
        MasterComputer master = null;
        for(Computer c: j.jenkins.getComputers()){
            if(c instanceof MasterComputer){
                master = (MasterComputer)c;
                break;
            }
        }
        if(master == null){
            Assert.fail("Unable to find the master computer.");
        }

        if(master.isUnix()){
            p.getBuildersList().add(new Shell("echo foo"));
        } else {
            p.getBuildersList().add(new BatchFile("echo foo"));
        }
        p.scheduleBuild2(0).get();
        assertLogs("foo", true);
    }

    @Test
    public void testPipeline() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "pipeline");
        p.setDefinition(new CpsFlowDefinition("echo 'foo'\n", true));
        p.scheduleBuild2(0).get();
        assertLogs("foo", false);
    }

    @Test
    public void testPipelineWithTraces() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);

        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "pipeline-traces");
        p.setDefinition(new CpsFlowDefinition("echo 'foo'\n", true));
        p.scheduleBuild2(0).get();
        assertLogs("foo", true);
    }

    @Test
    public void testPipelineOnWorkerNode() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "pipelineOnWorkerNode");
        p.setDefinition(new CpsFlowDefinition("node('test') {echo 'foo'}\n", true));
        p.scheduleBuild2(0).get();
        assertLogs("foo", false);
    }

    @Test
    public void testPipelineOnWorkerNodeWithTraces() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);

        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "pipelineOnWorkerNode-traces");
        p.setDefinition(new CpsFlowDefinition("node('test') {echo 'foo'}\n", true));
        p.scheduleBuild2(0).get();
        assertLogs("foo", true);
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
