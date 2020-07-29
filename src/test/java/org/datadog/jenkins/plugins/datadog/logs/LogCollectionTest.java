package org.datadog.jenkins.plugins.datadog.logs;

import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.util.logging.Logger;
import jenkins.model.Jenkins.MasterComputer;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class LogCollectionTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    private static DatadogClientStub stubClient = new DatadogClientStub();

    @BeforeClass
    public static void setup() throws Exception {
        ClientFactory.setTestClient(stubClient);
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        ExtensionList.clearLegacyInstances();
        cfg.setCollectBuildLogs(true);
        j.createOnlineSlave(new LabelAtom("test"));
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
        Assert.assertTrue(stubClient.logLines.contains("foo"));
    }

    @Test
    public void testPipeline() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "pipeline");
        p.setDefinition(new CpsFlowDefinition("echo 'foo'\n", true));
        p.scheduleBuild2(0).get();
        Assert.assertTrue(stubClient.logLines.contains("foo"));
    }

    @Test
    public void testPipelineOnWorkerNode() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "pipelineOnWorkerNode");
        p.setDefinition(new CpsFlowDefinition("node('test') {echo 'foo'}\n", true));
        p.scheduleBuild2(0).get();
        Assert.assertTrue(stubClient.logLines.contains("foo"));
    }

}
