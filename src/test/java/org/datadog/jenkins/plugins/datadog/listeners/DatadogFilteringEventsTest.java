package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.contrib.java.lang.system.EnvironmentVariables;

import org.acegisecurity.userdetails.UserDetails;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogJobProperty;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.events.*;
import org.datadog.jenkins.plugins.datadog.stubs.BuildStub;
import org.datadog.jenkins.plugins.datadog.stubs.ProjectStub;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;

public class DatadogFilteringEventsTest {
    private DatadogClientStub client;

    private DatadogBuildListener datadogBuildListener;
    private DatadogSCMListener datadogSCMListener;
    private DatadogComputerListener datadogComputerListener;
    private DatadogItemListener datadogItemListener;
    private DatadogSaveableListener datadogSaveableListener;
    private DatadogSecurityListener datadogSecurityListener;

    private ProjectStub job;

    @ClassRule
    private static final JenkinsRule jenkinsRule = new JenkinsRule();  
    
    @Rule
    private final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void setUp() throws Exception {
        this.client = new DatadogClientStub();
        ClientFactory.setTestClient(this.client);

        this.datadogBuildListener = new DatadogBuildListener();
        this.datadogComputerListener = new DatadogComputerListener();
        this.datadogSCMListener = new DatadogSCMListener();
        this.datadogItemListener = new DatadogItemListener();
        this.datadogSaveableListener = new DatadogSaveableListener();
        this.datadogSecurityListener = new DatadogSecurityListener();

        this.job = new ProjectStub(jenkinsRule.jenkins,"JobName");
        this.job.addProperty(new DatadogJobProperty<>());
        this.client.events.clear(); // adding property for scm checkout adds a config changed event
        
        // Default config settings
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSecurityEvents(true);
        cfg.setEmitSystemEvents(true);
        cfg.setEmitConfigChangeEvents(false);
        cfg.setIncludeEvents("");
        cfg.setExcludeEvents("");
    }

    @Test
    public void includeAllEventsViaToggle() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitConfigChangeEvents(true);

        this.assertAllIncludedEvents();

        assertTrue(this.client.events.size() == 18);
    }

    @Test
    public void includeAllEventsViaStringInput() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSecurityEvents(false);
        cfg.setEmitSystemEvents(false);
        cfg.setIncludeEvents(String.format("%s,%s,%s", DatadogGlobalConfiguration.SYSTEM_EVENTS, 
            DatadogGlobalConfiguration.SECURITY_EVENTS, DatadogGlobalConfiguration.CONFIG_CHANGED_EVENT));

        this.assertAllIncludedEvents();
        assertTrue(this.client.events.size() == 18);
    }

    @Test
    public void excludeAllEventsViaToggle() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSecurityEvents(false);
        cfg.setEmitSystemEvents(false);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);

        this.runAllEvents();
        assertTrue(this.client.events.size() == 0);
    }

    @Test
    public void excludeAllEventsViaStringInput() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitConfigChangeEvents(true);

        String allEvents = String.format("%s,%s,%s,%s", DatadogGlobalConfiguration.DEFAULT_EVENTS,
            DatadogGlobalConfiguration.SYSTEM_EVENTS, DatadogGlobalConfiguration.SECURITY_EVENTS, DatadogGlobalConfiguration.CONFIG_CHANGED_EVENT);

        cfg.setExcludeEvents(allEvents);

        this.runAllEvents();
        assertTrue(this.client.events.size() == 0);
    }

    @Test
    public void includeOnlySecurityEventsViaToggle() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSystemEvents(false);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);

        this.runAllEvents();
        this.assertSecurityEvents();
        assertTrue(this.client.events.size() == 3); 
    }

    @Test
    public void includeOnlySecurityEventsViaStringInput() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSystemEvents(false);
        cfg.setEmitSecurityEvents(false);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);
        cfg.setIncludeEvents(DatadogGlobalConfiguration.SECURITY_EVENTS);

        this.runAllEvents();
        this.assertSecurityEvents();

        assertTrue(this.client.events.size() == 3); 
    }

    @Test
    public void includeOnlySystemEventsViaToggle() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSecurityEvents(false);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);

        this.runAllEvents();
        this.assertSystemEvents();
        assertTrue(this.client.events.size() == 10);
    }

    @Test
    public void includeOnlySystemEventsViaStringInput() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSystemEvents(false);
        cfg.setEmitSecurityEvents(false);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);
        cfg.setIncludeEvents(DatadogGlobalConfiguration.SYSTEM_EVENTS);

        this.runAllEvents();
        this.assertSystemEvents();

        assertTrue(this.client.events.size() == 10);
    }

    @Test
    public void includeOnlyDefaultEventsViaStringInput() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSystemEvents(false);
        cfg.setEmitSecurityEvents(false);

        this.runAllEvents();
        this.assertDefaultEvents();
        assertTrue(this.client.events.size() == 4);
    }

    @Test
    public void excludeOnlySecurityEventsViaToggle() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSecurityEvents(false);
        cfg.setEmitConfigChangeEvents(true);
    
        this.runDefaultEvents();
        this.assertDefaultEvents();

        this.runSystemEvents();
        this.assertSystemEvents();

        this.runConfigChangeEvent();
        this.assertConfigChangeEvent();

        this.runSecurityEvents();

        assertTrue(this.client.events.size() == 15); 
    }

    @Test
    public void excludeOnlySecurityEventsViaStringInput() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitConfigChangeEvents(true);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.SECURITY_EVENTS);

        this.runDefaultEvents();
        this.assertDefaultEvents();

        this.runSystemEvents();
        this.assertSystemEvents();

        this.runConfigChangeEvent();
        this.assertConfigChangeEvent();

        this.runSecurityEvents();

        assertTrue(this.client.events.size() == 15); 
    }

    @Test
    public void excludeOnlySystemEventsViaToggle() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSystemEvents(false);    

        this.runDefaultEvents();
        this.assertDefaultEvents();

        this.runSystemEvents();
        this.runConfigChangeEvent();

        this.runSecurityEvents();
        this.assertSecurityEvents();

        assertTrue(this.client.events.size() == 7); 
    }

    @Test
    public void excludeOnlySystemEventsViaStringInput() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitConfigChangeEvents(true);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.SYSTEM_EVENTS + "," + DatadogGlobalConfiguration.CONFIG_CHANGED_EVENT);

        this.runDefaultEvents();
        this.assertDefaultEvents();

        this.runSystemEvents();
        this.runConfigChangeEvent();

        this.runSecurityEvents();
        this.assertSecurityEvents();

        assertTrue(this.client.events.size() == 7); 
    }

    @Test
    public void excludeOnlyDefaultEventsViaStringInput() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitConfigChangeEvents(true);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);

        this.runDefaultEvents();
        this.runSystemEvents();
        this.assertSystemEvents();
        this.runConfigChangeEvent();
        this.assertConfigChangeEvent();
        this.runSecurityEvents();
        this.assertSecurityEvents();

        assertTrue(this.client.events.size() == 14); 
    }

    @Test
    public void includeMultipleEventsOfDifferentTypes() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSystemEvents(false);
        cfg.setEmitSecurityEvents(false);
        cfg.setExcludeEvents("BuildAborted,BuildCompleted,SCMCheckout");
        cfg.setIncludeEvents("ComputerOnline,UserAuthenticated");
        
        this.runAllEvents();
        assertTrue(this.client.events.get(0).getEvent() instanceof BuildStartedEventImpl);

        DatadogEvent computerEvent = this.client.events.get(1).getEvent();
        assertTrue(computerEvent instanceof ComputerOnlineEventImpl);
        assertFalse(((ComputerOnlineEventImpl) computerEvent).isTemporarily());

        DatadogEvent authEvent = this.client.events.get(2).getEvent();
        assertTrue(authEvent instanceof UserAuthenticationEventImpl);
        assertEquals(((UserAuthenticationEventImpl) authEvent).getAction(), UserAuthenticationEventImpl.LOGIN);

        assertTrue(this.client.events.size() == 3);
    }

    @Test
    public void includeAllOneEventTypeExcludeOneByName() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSystemEvents(false);
        cfg.setExcludeEvents("UserAuthenticated," + DatadogGlobalConfiguration.DEFAULT_EVENTS);

        this.runAllEvents();
        DatadogEvent authEvent = this.client.events.get(0).getEvent();
        assertTrue(authEvent instanceof UserAuthenticationEventImpl);
        assertEquals(((UserAuthenticationEventImpl) authEvent).getAction(), UserAuthenticationEventImpl.ACCESS_DENIED);

        authEvent = this.client.events.get(1).getEvent();
        assertTrue(authEvent instanceof UserAuthenticationEventImpl);
        assertEquals(((UserAuthenticationEventImpl) authEvent).getAction(), UserAuthenticationEventImpl.LOGOUT);

        assertTrue(this.client.events.size() == 2);
    }

    @Test
    public void excludeAllOneEventTypeIncludeOneByName() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSystemEvents(false);
        cfg.setEmitSecurityEvents(false);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);
        cfg.setIncludeEvents("ComputerOnline");

        this.runAllEvents();
        DatadogEvent computerEvent = this.client.events.get(0).getEvent();
        assertTrue(computerEvent instanceof ComputerOnlineEventImpl);
        assertFalse(((ComputerOnlineEventImpl) computerEvent).isTemporarily());

        assertTrue(this.client.events.size() == 1);
    }

    @Test
    public void configOnSystemOffViaToggle() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSystemEvents(false);
        cfg.setEmitSecurityEvents(false);
        cfg.setEmitConfigChangeEvents(true);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);

        this.runAllEvents();
        assertTrue(this.client.events.isEmpty());
    }

    @Test
    public void configOnSystemOn() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSecurityEvents(false);
        cfg.setEmitConfigChangeEvents(true);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);

        this.runDefaultEvents();
        this.runSystemEvents();
        this.assertSystemEvents();
        this.runConfigChangeEvent();
        this.assertConfigChangeEvent();
        this.runSecurityEvents();

        assert(this.client.events.size() == 11);
    }

    @Test
    public void configOffSystemOn() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSecurityEvents(false);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);

        this.runAllEvents();
        this.assertSystemEvents();

        assert(this.client.events.size() == 10);
    }

    @Test
    public void configOffSystemOff() throws Exception {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEmitSecurityEvents(false);
        cfg.setEmitSystemEvents(false);
        cfg.setExcludeEvents(DatadogGlobalConfiguration.DEFAULT_EVENTS);

        this.runAllEvents();

        assert(this.client.events.isEmpty());
    }

    @Test
    public void testWithEnvVars() throws Exception {
        environmentVariables.set("DATADOG_JENKINS_PLUGIN_EMIT_SECURITY_EVENTS", "false");
        environmentVariables.set("DATADOG_JENKINS_PLUGIN_EMIT_CONFIG_CHANGE_EVENTS", "false");
        environmentVariables.set("DATADOG_JENKINS_PLUGIN_EMIT_SYSTEM_EVENTS", "true");
        environmentVariables.set("DATADOG_JENKINS_PLUGIN_EXCLUDE_EVENTS", DatadogGlobalConfiguration.DEFAULT_EVENTS);

        DatadogUtilities.getDatadogGlobalDescriptor().loadEnvVariables();
        
        this.runAllEvents();
        this.assertSystemEvents();
    }

    @Test
    public void testWithEnvVarsPart2() throws Exception {
        environmentVariables.set("DATADOG_JENKINS_PLUGIN_EMIT_SECURITY_EVENTS", "false");
        environmentVariables.set("DATADOG_JENKINS_PLUGIN_EMIT_CONFIG_CHANGE_EVENTS", "false");
        environmentVariables.set("DATADOG_JENKINS_PLUGIN_EMIT_SYSTEM_EVENTS", "false");

        DatadogUtilities.getDatadogGlobalDescriptor().loadEnvVariables();
        
        this.runAllEvents();
        this.assertDefaultEvents();
    }

    private void assertAllIncludedEvents() throws Exception {
        this.runDefaultEvents();
        this.assertDefaultEvents();

        this.runSystemEvents();
        this.assertSystemEvents();

        this.runConfigChangeEvent();
        this.assertConfigChangeEvent();

        this.runSecurityEvents();
        this.assertSecurityEvents();
    }

    private void runAllEvents() throws Exception {
        this.runDefaultEvents();
        this.runSystemEvents();
        this.runConfigChangeEvent();
        this.runSecurityEvents();
    }

    private void runDefaultEvents() throws Exception {
        BuildStub previousSuccessfulRun = new BuildStub(this.job, Result.SUCCESS, null, null,
                121000L, 1, null, 1000000L, null);

        BuildStub successRun = new BuildStub(this.job, Result.SUCCESS, null, previousSuccessfulRun,
                124000L, 4, previousSuccessfulRun, 4000000L, null);

        BuildStub previousFailedRun1 = new BuildStub(this.job, Result.NOT_BUILT, null, previousSuccessfulRun,
                122000L, 2, previousSuccessfulRun, 2000000L, null);

        BuildStub checkoutRun = new BuildStub(this.job, Result.SUCCESS, null, previousSuccessfulRun,
                122000L, 2, previousSuccessfulRun, 2000000L, null);
       
        this.datadogBuildListener.onStarted(successRun, mock(TaskListener.class));
        this.datadogBuildListener.onCompleted(successRun, mock(TaskListener.class));
        this.datadogBuildListener.onDeleted(previousFailedRun1);
        this.datadogSCMListener.onCheckout(checkoutRun, null, null, mock(TaskListener.class), null, null);
    }

    private void assertDefaultEvents() throws Exception {
        int size = this.client.events.size();

        assertTrue(this.client.events.get(size - 4).getEvent() instanceof BuildStartedEventImpl);
        assertTrue(this.client.events.get(size - 3).getEvent() instanceof BuildFinishedEventImpl);
        assertTrue(this.client.events.get(size - 2).getEvent() instanceof BuildAbortedEventImpl);
        assertTrue(this.client.events.get(size - 1).getEvent() instanceof SCMCheckoutCompletedEventImpl);
    }

    private void runSystemEvents() throws Exception {
        this.datadogComputerListener.onOnline(mock(SlaveComputer.class), mock(TaskListener.class));
        this.datadogComputerListener.onOffline(mock(SlaveComputer.class), OfflineCause.create(null));
        this.datadogComputerListener.onTemporarilyOnline(mock(SlaveComputer.class));
        this.datadogComputerListener.onTemporarilyOffline(mock(SlaveComputer.class), OfflineCause.create(null));
        this.datadogComputerListener.onLaunchFailure(mock(SlaveComputer.class), mock(TaskListener.class));
        this.datadogItemListener.onCopied(job, job);
        this.datadogItemListener.onCreated(job);
        this.datadogItemListener.onUpdated(job);
        this.datadogItemListener.onDeleted(job);
        this.datadogItemListener.onLocationChanged(job, null, null);
    }

    private void assertSystemEvents() throws Exception {
        int size = this.client.events.size();

        DatadogEvent computerEvent = this.client.events.get(size - 10).getEvent();
        assertTrue(computerEvent instanceof ComputerOnlineEventImpl);
        assertFalse(((ComputerOnlineEventImpl) computerEvent).isTemporarily());

        computerEvent = this.client.events.get(size - 9).getEvent();
        assertTrue(computerEvent instanceof ComputerOfflineEventImpl);
        assertFalse(((ComputerOfflineEventImpl) computerEvent).isTemporarily());

        computerEvent = this.client.events.get(size - 8).getEvent();
        assertTrue(computerEvent instanceof ComputerOnlineEventImpl);
        assertTrue(((ComputerOnlineEventImpl) computerEvent).isTemporarily());

        computerEvent = this.client.events.get(size - 7).getEvent();
        assertTrue(computerEvent instanceof ComputerOfflineEventImpl);
        assertTrue(((ComputerOfflineEventImpl) computerEvent).isTemporarily());
        
        assertTrue(this.client.events.get(size - 6).getEvent() instanceof ComputerLaunchFailedEventImpl);

        assertTrue(this.client.events.get(size - 5).getEvent() instanceof ItemCopiedEventImpl);

        DatadogEvent itemEvent = this.client.events.get(size - 4).getEvent();
        assertTrue(itemEvent instanceof ItemCRUDEventImpl);
        assertEquals(((ItemCRUDEventImpl) itemEvent).getAction(), ItemCRUDEventImpl.CREATED);

        itemEvent = this.client.events.get(size - 3).getEvent();
        assertTrue(itemEvent instanceof ItemCRUDEventImpl);
        assertEquals(((ItemCRUDEventImpl) itemEvent).getAction(), ItemCRUDEventImpl.UPDATED);

        itemEvent = this.client.events.get(size - 2).getEvent();
        assertTrue(itemEvent instanceof ItemCRUDEventImpl);
        assertEquals(((ItemCRUDEventImpl) itemEvent).getAction(), ItemCRUDEventImpl.DELETED);

        assertTrue(this.client.events.get(size - 1).getEvent() instanceof ItemLocationChangedEventImpl);
    }

    private void runConfigChangeEvent() throws Exception {
        BuildStub previousSuccessfulRun = new BuildStub(this.job, Result.SUCCESS, null, null,
            121000L, 1, null, 1000000L, null);
        this.datadogSaveableListener.onChange(previousSuccessfulRun, null);
    }

    private void assertConfigChangeEvent() throws Exception {
        int size = this.client.events.size();
        assertTrue(this.client.events.get(size - 1).getEvent() instanceof ConfigChangedEventImpl);
    }

    private void runSecurityEvents() throws Exception {
        this.datadogSecurityListener.authenticated(mock(UserDetails.class));
        this.datadogSecurityListener.failedToAuthenticate("testUser");
        this.datadogSecurityListener.loggedOut("testUser");
    }

    private void assertSecurityEvents() throws Exception {
        int size = this.client.events.size();
        
        DatadogEvent authEvent = this.client.events.get(size - 3).getEvent();
        assertTrue(authEvent instanceof UserAuthenticationEventImpl);
        assertEquals(((UserAuthenticationEventImpl) authEvent).getAction(), UserAuthenticationEventImpl.LOGIN);

        authEvent = this.client.events.get(size - 2).getEvent();
        assertTrue(authEvent instanceof UserAuthenticationEventImpl);
        assertEquals(((UserAuthenticationEventImpl) authEvent).getAction(), UserAuthenticationEventImpl.ACCESS_DENIED);

        authEvent = this.client.events.get(size - 1).getEvent();
        assertTrue(authEvent instanceof UserAuthenticationEventImpl);
        assertEquals(((UserAuthenticationEventImpl) authEvent).getAction(), UserAuthenticationEventImpl.LOGOUT);
    }
}
