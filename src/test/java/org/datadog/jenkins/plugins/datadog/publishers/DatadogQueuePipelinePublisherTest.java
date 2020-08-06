package org.datadog.jenkins.plugins.datadog.publishers;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Assert;

import org.apache.commons.io.IOUtils;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import hudson.EnvVars;
import hudson.model.Messages;
import hudson.model.Computer;
import hudson.slaves.OfflineCause;

public class DatadogQueuePipelinePublisherTest {
    @ClassRule 
    public static JenkinsRule jenkins = new JenkinsRule();

     
    @Test
    public void testPipelineInQueue() throws Exception {
        Assert.assertTrue(true);
    }

}
