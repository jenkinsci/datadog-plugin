package org.datadog.jenkins.plugins.datadog.logs;

import static org.mockito.Mockito.when;

import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.util.logging.Logger;
import jenkins.model.Jenkins.MasterComputer;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.jvnet.hudson.test.JenkinsRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.crypto.*", "javax.xml.*", "java.xml.*", "jdk.xml.*" })
@PowerMockRunnerDelegate(BlockJUnit4ClassRunner.class)
@PrepareForTest(ClientFactory.class)
public class LogCollectionTest {
    private static final Logger LOGGER = Logger.getLogger(LogCollectionTest.class.getName());

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    private DatadogClientStub stubClient = new DatadogClientStub();

    @Before
    public void setup()
    {
        PowerMockito.mockStatic(ClientFactory.class);
        when(ClientFactory.getClient()).thenReturn(stubClient);
        DatadogGlobalConfiguration cfg = ExtensionList.lookup(DatadogGlobalConfiguration.class).get(0);
        cfg.setCollectBuildLogs(true);
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
        for(String l : stubClient.logLines) {
            LOGGER.severe(l);
        }
        Assert.assertTrue(stubClient.logLines.contains("foo"));
    }

    @Test
    public void testPipeline() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo 'foo'\n", true));
        p.scheduleBuild2(0).get();
        Assert.assertTrue(stubClient.logLines.contains("foo"));
    }
}
