/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package org.datadog.jenkins.plugins.datadog;

import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Result;
import org.apache.commons.math3.exception.NullArgumentException;
import org.jenkinsci.plugins.workflow.actions.*;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DatadogUtilitiesTest {

    public DatadogGlobalConfiguration cfg;

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void setUpMocks() {
        cfg = DatadogUtilities.getDatadogGlobalDescriptor();
    }

    @Test
    public void testCstrToList(){
        assertTrue(DatadogUtilities.cstrToList(null).isEmpty());
        assertTrue(DatadogUtilities.cstrToList("").isEmpty());
        assertTrue(DatadogUtilities.cstrToList(" , ").isEmpty());

        List<String> items = new ArrayList<>();
        items.add("item1");
        assertTrue(DatadogUtilities.cstrToList("item1").equals(items));
        assertTrue(DatadogUtilities.cstrToList(" item1 ").equals(items));
        assertTrue(DatadogUtilities.cstrToList(" , item1 , ").equals(items));

        items = new ArrayList<>();
        items.add("item1");
        items.add("item2");
        assertTrue(DatadogUtilities.cstrToList("item1,item2").equals(items));
        assertTrue(DatadogUtilities.cstrToList("  item1 , item2 ").equals(items));
        assertTrue(DatadogUtilities.cstrToList(" , item1 , item2 , ").equals(items));
    }

    @Test
    public void testLinesToList(){
        assertTrue(DatadogUtilities.linesToList(null).isEmpty());
        assertTrue(DatadogUtilities.linesToList("").isEmpty());

        List<String> items = new ArrayList<>();
        items.add("item1");
        assertTrue(DatadogUtilities.linesToList("item1").equals(items));
        assertTrue(DatadogUtilities.linesToList(" item1 ").equals(items));
        assertTrue(DatadogUtilities.linesToList(" \n item1 \n ").equals(items));

        items = new ArrayList<>();
        items.add("item1");
        items.add("item2");
        assertTrue(DatadogUtilities.linesToList("item1\nitem2").equals(items));
        assertTrue(DatadogUtilities.linesToList("  item1 \n item2 ").equals(items));
        assertTrue(DatadogUtilities.linesToList(" \n item1 \n item2 \n ").equals(items));
    }


    @Test
    public void isStageNodeTest() {
        assertFalse(DatadogUtilities.isStageNode(null));
        BlockStartNode node = mock(BlockStartNode.class);
        assertFalse(DatadogUtilities.isStageNode(node));

        when(node.getAction(LabelAction.class)).thenReturn(mock(LabelAction.class));
        assertTrue(DatadogUtilities.isStageNode(node));

        when(node.getAction(ThreadNameAction.class)).thenReturn(mock(ThreadNameAction.class));
        assertFalse(DatadogUtilities.isStageNode(node));
    }

    @Test
    public void getResultTagTest(){
        // passed with null
        Assert.assertThrows(NullPointerException.class, () -> {
            DatadogUtilities.getResultTag(null);
        });

        FlowNode node = mock(FlowNode.class);

        // when getError returns an error
        when(node.getError()).thenReturn(new ErrorAction(new NullArgumentException()));
        assertEquals(DatadogUtilities.getResultTag(node), "ERROR");

        // when there's a warning action
        when(node.getError()).thenReturn(null);
        when(node.getPersistentAction(WarningAction.class)).thenReturn(new WarningAction(Result.SUCCESS));
        assertEquals(DatadogUtilities.getResultTag(node), "SUCCESS");

        when(node.getPersistentAction(WarningAction.class)).thenReturn(new WarningAction(Result.NOT_BUILT));
        assertEquals(DatadogUtilities.getResultTag(node), "NOT_BUILT");

        // when the result is unknown
        when(node.getPersistentAction(WarningAction.class)).thenReturn(null);
        assertEquals(DatadogUtilities.getResultTag(node), "SUCCESS");

        // when the node is a Stage node and the stage is skipped
        BlockStartNode startNode = mock(BlockStartNode.class);
        TagsAction tagsAction = new TagsAction();
        tagsAction.addTag("STAGE_STATUS", "SKIPPED_FOR_UNSTABLE");
        when(startNode.getPersistentAction(TagsAction.class)).thenReturn(tagsAction);
        assertEquals(DatadogUtilities.getResultTag(startNode), "SKIPPED");

        // when the node is a BlockEndNode and the stage containing it was skipped
        BlockEndNode endNode = mock(BlockEndNode.class);
        when(endNode.getStartNode()).thenReturn(startNode);
        assertEquals(DatadogUtilities.getResultTag(endNode), "SKIPPED");


    }

    @Test
    public void testToJsonSet() {
        Assert.assertNull(DatadogUtilities.toJson((Set<String>)null));
        Assert.assertNull(DatadogUtilities.toJson(new HashSet<>()));

        final Set<String> oneItem = new HashSet<>();
        oneItem.add("item1");
        assertEquals("[\"item1\"]", DatadogUtilities.toJson(oneItem));

        final Set<String> multipleItems = new LinkedHashSet<>();
        multipleItems.add("item1");
        multipleItems.add("item2");
        multipleItems.add("item3");
        assertEquals("[\"item1\",\"item2\",\"item3\"]", DatadogUtilities.toJson(multipleItems));
    }

    @Test
    public void testToJsonMap() {
        Assert.assertNull(DatadogUtilities.toJson((Map<String,String>)null));
        Assert.assertNull(DatadogUtilities.toJson(new HashMap<>()));

        final Map<String, String> oneItem = new HashMap<>();
        oneItem.put("itemKey1","itemValue1");
        assertEquals("{\"itemKey1\":\"itemValue1\"}", DatadogUtilities.toJson(oneItem));

        final Map<String, String> multipleItems = new LinkedHashMap<>();
        multipleItems.put("itemKey1", "itemValue1");
        multipleItems.put("itemKey2", "itemValue2");
        multipleItems.put("itemKey3", "itemValue3");
        assertEquals("{\"itemKey1\":\"itemValue1\",\"itemKey2\":\"itemValue2\",\"itemKey3\":\"itemValue3\"}", DatadogUtilities.toJson(multipleItems));
    }

    @Test
    public void testGetHostname() throws IOException {
        try (MockedStatic datadogUtilities = Mockito.mockStatic(DatadogUtilities.class)) {
            datadogUtilities.when(() -> DatadogUtilities.getDatadogGlobalDescriptor()).thenReturn(cfg);
            HttpURLConnection mockHTTP = mock(HttpURLConnection.class);

            datadogUtilities.when(() -> DatadogUtilities.getAwsInstanceID()).thenReturn("test");
            datadogUtilities.when(() -> DatadogUtilities.getHostname(null)).thenCallRealMethod();

            String hostname = DatadogUtilities.getHostname(null);
            Assert.assertNotEquals("test", hostname);

            cfg.setUseAwsInstanceHostname(true);

            hostname = DatadogUtilities.getHostname(null);
            assertEquals("test", hostname);

            cfg.setUseAwsInstanceHostname(false);
            hostname = DatadogUtilities.getHostname(null);
            Assert.assertNotEquals("test", hostname);
        }
    }

    @Test
    public void testIsPrivateIPv4Address() {
        assertFalse(DatadogUtilities.isPrivateIPv4Address(null));
        assertFalse(DatadogUtilities.isPrivateIPv4Address(""));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("google.com"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("my.subdomain.domain.com"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("123.456.789.012"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("10.0.my-domain.com"));
        assertTrue(DatadogUtilities.isPrivateIPv4Address("10.0.0.1"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("10.0.0.1.1"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("10.0.0.1.org"));
        assertTrue(DatadogUtilities.isPrivateIPv4Address("10.255.255.255"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("10.255.255.256"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("10.255.256.255"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("10.-1.255.255"));
        assertTrue(DatadogUtilities.isPrivateIPv4Address("172.16.0.1"));
        assertTrue(DatadogUtilities.isPrivateIPv4Address("172.31.0.1"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("172.15.0.1"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("172.32.0.1"));
        assertTrue(DatadogUtilities.isPrivateIPv4Address("192.168.0.1"));
        assertTrue(DatadogUtilities.isPrivateIPv4Address("192.168.255.255"));
        assertFalse(DatadogUtilities.isPrivateIPv4Address("192.167.255.255"));
    }

    @Test
    public void testComputerDDCIHostnameHasHighestPriority() throws Exception {
        Computer computer = givenComputerWithEnvVars(
                DatadogGlobalConfiguration.DD_CI_HOSTNAME, "computer-dd-ci-hostname",
                "HOSTNAME", "computer-hostname");
        EnvVars jobEnvVars = givenEnvVars(
                DatadogGlobalConfiguration.DD_CI_HOSTNAME, "job-dd-ci-hostname",
                "HOSTNAME", "job-hostname");
        assertEquals("computer-dd-ci-hostname", DatadogUtilities.getNodeHostname(jobEnvVars, computer));
    }

    @Test
    public void testJobDDCIHostnameHasHigherPriorityThanComputerHostname() throws Exception {
        Computer computer = givenComputerWithEnvVars("HOSTNAME", "computer-hostname");
        EnvVars jobEnvVars = givenEnvVars(
                DatadogGlobalConfiguration.DD_CI_HOSTNAME, "job-dd-ci-hostname",
                "HOSTNAME", "job-hostname");
        assertEquals("job-dd-ci-hostname", DatadogUtilities.getNodeHostname(jobEnvVars, computer));
    }

    @Test
    public void testComputerHostnameHasHigherPriorityThanJobHostname() throws Exception {
        Computer computer = givenComputerWithEnvVars("HOSTNAME", "computer-hostname");
        EnvVars jobEnvVars = givenEnvVars("HOSTNAME", "job-hostname");
        assertEquals("computer-hostname", DatadogUtilities.getNodeHostname(jobEnvVars, computer));
    }

    @Test
    public void testJobHostname() throws Exception {
        Computer computer = givenComputerWithEnvVars();
        EnvVars jobEnvVars = givenEnvVars("HOSTNAME", "job-hostname");
        assertEquals("job-hostname", DatadogUtilities.getNodeHostname(jobEnvVars, computer));
    }

    private static Computer givenComputerWithEnvVars(String... vars) throws Exception {
        EnvVars computerEnvVars = givenEnvVars(vars);
        Computer computer = mock(Computer.class);
        when(computer.getEnvironment()).thenReturn(computerEnvVars);
        return computer;
    }

    private static EnvVars givenEnvVars(String... vars) {
        EnvVars envVars = new EnvVars();
        for (int i = 0; i < vars.length; i += 2) {
            envVars.put(vars[i], vars[i + 1]);
        }
        return envVars;
    }

}
