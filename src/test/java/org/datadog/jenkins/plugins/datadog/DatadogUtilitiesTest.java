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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Result;
import org.apache.commons.math3.exception.NullArgumentException;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class DatadogUtilitiesTest {

    @Test
    public void testCstrToList(){
        Assert.assertTrue(DatadogUtilities.cstrToList(null).isEmpty());
        Assert.assertTrue(DatadogUtilities.cstrToList("").isEmpty());
        Assert.assertTrue(DatadogUtilities.cstrToList(" , ").isEmpty());

        List<String> items = new ArrayList<>();
        items.add("item1");
        Assert.assertTrue(DatadogUtilities.cstrToList("item1").equals(items));
        Assert.assertTrue(DatadogUtilities.cstrToList(" item1 ").equals(items));
        Assert.assertTrue(DatadogUtilities.cstrToList(" , item1 , ").equals(items));

        items = new ArrayList<>();
        items.add("item1");
        items.add("item2");
        Assert.assertTrue(DatadogUtilities.cstrToList("item1,item2").equals(items));
        Assert.assertTrue(DatadogUtilities.cstrToList("  item1 , item2 ").equals(items));
        Assert.assertTrue(DatadogUtilities.cstrToList(" , item1 , item2 , ").equals(items));
    }

    @Test
    public void testLinesToList(){
        Assert.assertTrue(DatadogUtilities.linesToList(null).isEmpty());
        Assert.assertTrue(DatadogUtilities.linesToList("").isEmpty());

        List<String> items = new ArrayList<>();
        items.add("item1");
        Assert.assertTrue(DatadogUtilities.linesToList("item1").equals(items));
        Assert.assertTrue(DatadogUtilities.linesToList(" item1 ").equals(items));
        Assert.assertTrue(DatadogUtilities.linesToList(" \n item1 \n ").equals(items));

        items = new ArrayList<>();
        items.add("item1");
        items.add("item2");
        Assert.assertTrue(DatadogUtilities.linesToList("item1\nitem2").equals(items));
        Assert.assertTrue(DatadogUtilities.linesToList("  item1 \n item2 ").equals(items));
        Assert.assertTrue(DatadogUtilities.linesToList(" \n item1 \n item2 \n ").equals(items));
    }


    @Test
    public void isStageNodeTest() {
        Assert.assertFalse(DatadogUtilities.isStageNode(null));
        BlockStartNode node = mock(BlockStartNode.class);
        Assert.assertFalse(DatadogUtilities.isStageNode(node));

        when(node.getAction(LabelAction.class)).thenReturn(mock(LabelAction.class));
        Assert.assertTrue(DatadogUtilities.isStageNode(node));

        when(node.getAction(ThreadNameAction.class)).thenReturn(mock(ThreadNameAction.class));
        Assert.assertFalse(DatadogUtilities.isStageNode(node));
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
        Assert.assertEquals(DatadogUtilities.getResultTag(node), "ERROR");

        // when there's a warning action
        when(node.getError()).thenReturn(null);
        when(node.getPersistentAction(WarningAction.class)).thenReturn(new WarningAction(Result.SUCCESS));
        Assert.assertEquals(DatadogUtilities.getResultTag(node), "SUCCESS");

        when(node.getPersistentAction(WarningAction.class)).thenReturn(new WarningAction(Result.NOT_BUILT));
        Assert.assertEquals(DatadogUtilities.getResultTag(node), "NOT_BUILT");

        // when the result is unknown
        when(node.getPersistentAction(WarningAction.class)).thenReturn(null);
        Assert.assertEquals(DatadogUtilities.getResultTag(node), "SUCCESS");

        // when the node is a Stage node and the stage is skipped
        BlockStartNode startNode = mock(BlockStartNode.class);
        TagsAction tagsAction = new TagsAction();
        tagsAction.addTag("STAGE_STATUS", "SKIPPED_FOR_UNSTABLE");
        when(startNode.getPersistentAction(TagsAction.class)).thenReturn(tagsAction);
        Assert.assertEquals(DatadogUtilities.getResultTag(startNode), "SKIPPED");

        // when the node is a BlockEndNode and the stage containing it was skipped
        BlockEndNode endNode = mock(BlockEndNode.class);
        when(endNode.getStartNode()).thenReturn(startNode);
        Assert.assertEquals(DatadogUtilities.getResultTag(endNode), "SKIPPED");


    }

}
