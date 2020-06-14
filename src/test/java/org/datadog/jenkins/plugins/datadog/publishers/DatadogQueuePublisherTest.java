package org.datadog.jenkins.plugins.datadog.publishers;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.stubs.QueueStub;

import hudson.model.Messages;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.Node.Mode;
import hudson.model.Queue.Item;
import hudson.AboutJenkins;
import hudson.ExtensionList;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.LoadBalancer;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Shell;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

//@RunWith(PowerMockRunner.class)
//@PrepareForTest(DatadogQueuePublisher.class)
public class DatadogQueuePublisherTest {
    @Rule 
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testQueueMetrics() throws Exception {
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new SleepBuilder(10000));
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("param", "0")));
        project.setConcurrentBuild(true);
        
        
        //DatadogQueuePublisher queuePublisher = spy(DatadogQueuePublisher.class);
        //DatadogClientStub client = new DatadogClientStub();
        //when(queuePublisher.getDatadogClient()).thenReturn(client);
        //((DatadogQueuePublisherTestWrapper)queuePublisher).setDatadogClient(client);  
        
        //Queue queue = jenkins.jenkins.getQueue();
        //((DatadogQueuePublisherTestWrapper)queuePublisher).setQueue(queue);
        
        for (int i = 0; i < 100; i++) {
            jenkins.jenkins.getQueue().schedule(project);
            //jenkins.getInstance().getQueue().maintain();
            //project.scheduleBuild(0, CAUSE, new ParametersAction(new StringParameterValue("param", String.valueOf(i))));
        }

        int length = jenkins.jenkins.getQueue().getItems().length;
        //Collection<Queue.LeftItem> leftItems = jenkins.jenkins.getQueue().getLeftItems();
        //Item[] items = (Item[]) leftItems.toArray(new Item[leftItems.size()]);
        //Queue testQueue = mock(Queue.class);
        //when(testQueue.getItems()).thenReturn(items);
        
        DatadogClientStub client = new DatadogClientStub();
        DatadogQueuePublisher queuePublisher = new DatadogQueuePublisherTestWrapper();
        ((DatadogQueuePublisherTestWrapper)queuePublisher).setDatadogClient(client);
        //Queue queue = new QueueStub(mock(LoadBalancer.class));
        //when(queue.getItems()).thenReturn(items);
        ((DatadogQueuePublisherTestWrapper)queuePublisher).setQueue(jenkins.jenkins.getQueue());        
        //when(testQueue.getBuildableItems()).thenReturn((List<Queue.BuildableItem>)Arrays.asList(items));
        //queuePublisher.queue = testQueue;
        //((DatadogQueuePublisherTestWrapper)queuePublisher).setQueue(queue);
        final String[] expectedTags = new String[1];
        expectedTags[0] = "jenkins_url:" + jenkins.getURL().toString();
        queuePublisher.doRun();
        client.assertMetric("jenkins.queue.size", length, "COMP11958.local", expectedTags);
    
    }
}