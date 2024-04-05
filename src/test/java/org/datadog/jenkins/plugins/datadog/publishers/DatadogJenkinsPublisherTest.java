package org.datadog.jenkins.plugins.datadog.publishers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.PluginManager;
import hudson.PluginManager.FailedPlugin;
import hudson.PluginWrapper;
import hudson.model.Project;
import java.util.Arrays;
import java.util.LinkedList;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class DatadogJenkinsPublisherTest {
    private DatadogClientStub client;
    private DatadogJenkinsPublisher queuePublisher;
    private Jenkins jenkins;
    private String hostname;
    private LinkedList<PluginWrapper> plugins;
    private LinkedList<FailedPlugin> failedPlugins;

    @Before
    public void setup() {
        client = new DatadogClientStub();
        queuePublisher = new DatadogJenkinsPublisher();
        ClientHolder.setClient(client);
        hostname = DatadogUtilities.getHostname(null);
        plugins = new LinkedList<>();
        failedPlugins = new LinkedList<>();
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugins()).thenReturn(plugins);
        when(pluginManager.getFailedPlugins()).thenReturn(failedPlugins);
        jenkins = mock(Jenkins.class);
        Project dummyProject = mock(Project.class);
        when(jenkins.getAllItems(Project.class)).thenReturn(Arrays.asList(dummyProject, dummyProject));
        when(jenkins.getPluginManager()).thenReturn(pluginManager);
        when(jenkins.getRootUrl()).thenReturn("dummy.hostname");
    }

    private void addPlugin(boolean active, boolean updateable, boolean failed) {
        PluginWrapper pluginMock = mock(PluginWrapper.class);
        when(pluginMock.hasUpdate()).thenReturn(updateable);
        when(pluginMock.isActive()).thenReturn(active);
        plugins.add(pluginMock);
        if(failed){
            FailedPlugin failedPluginMock = mock(FailedPlugin.class);
            failedPlugins.add(failedPluginMock);
        }
    }

    @Test
    public void testPluginCount() throws Exception {
        addPlugin(true, true, true);
        addPlugin(true, true, false);
        addPlugin(true, false, true);
        addPlugin(true, false, false);
        addPlugin(false, true, false);
        addPlugin(false, false, true);
        addPlugin(false, false, false);
        addPlugin(false, true, false);
        addPlugin(false, true, false);
        try(MockedStatic<Jenkins> jenkinsClass = Mockito.mockStatic(Jenkins.class)){
            jenkinsClass.when(Jenkins::getInstanceOrNull).thenReturn(jenkins);
            jenkinsClass.when(Jenkins::getInstance).thenReturn(jenkins);
            queuePublisher.doRun();
        }
        final String[] expectedTags = new String[1];
        expectedTags[0] = "jenkins_url:dummy.hostname";
        client.assertMetric("jenkins.plugin.count", 9, hostname, expectedTags);
        client.assertMetric("jenkins.plugin.active", 4, hostname, expectedTags);
        client.assertMetric("jenkins.plugin.failed", 3, hostname, expectedTags);
        client.assertMetric("jenkins.plugin.inactivate", 5, hostname, expectedTags);
        client.assertMetric("jenkins.plugin.withUpdate", 5, hostname, expectedTags);


    }
}