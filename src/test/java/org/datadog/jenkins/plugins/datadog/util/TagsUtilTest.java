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

package org.datadog.jenkins.plugins.datadog.util;

import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class TagsUtilTest {

    @Test
    public void testMerge(){
        Map<String, Set<String>> emptyTags = new HashMap<>();
        Assert.assertEquals(new HashMap<String, Set<String>>(), TagsUtil.merge(null, null));
        Assert.assertEquals(new HashMap<String, Set<String>>(), TagsUtil.merge(emptyTags, null));
        Assert.assertEquals(new HashMap<String, Set<String>>(), TagsUtil.merge(null, emptyTags));

        Map<String, Set<String>> assertionTags = new HashMap<>();
        assertionTags.put("name1", new HashSet<>());
        Map<String, Set<String>> nullTagValue = new HashMap<>();
        nullTagValue.put("name1", null);
        Assert.assertEquals(TagsUtil.merge(null, nullTagValue), assertionTags);
        Assert.assertEquals(TagsUtil.merge(nullTagValue, nullTagValue), assertionTags);
        Assert.assertEquals(TagsUtil.merge(nullTagValue, null).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(nullTagValue, emptyTags).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(emptyTags, nullTagValue).toString(), assertionTags.toString());
        Map<String, Set<String>> emptyTagValue = new HashMap<>();
        emptyTagValue.put("name1", new HashSet<>());
        Assert.assertEquals(TagsUtil.merge(emptyTagValue, null).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(null, emptyTagValue).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(emptyTagValue, emptyTags).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(emptyTags, emptyTagValue).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(nullTagValue, emptyTagValue).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(emptyTagValue, nullTagValue).toString(), assertionTags.toString());
        Map<String, Set<String>> n1v1Tag = new HashMap<>();
        n1v1Tag = DatadogClientStub.addTagToMap(n1v1Tag, "name1", "value1");
        assertionTags = new HashMap<>();
        assertionTags = DatadogClientStub.addTagToMap(assertionTags, "name1", "value1");
        Assert.assertEquals(TagsUtil.merge(n1v1Tag, null).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(null, n1v1Tag).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(n1v1Tag, emptyTags).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(emptyTags, n1v1Tag).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(nullTagValue, n1v1Tag).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(n1v1Tag, nullTagValue).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(n1v1Tag, emptyTagValue).toString(), assertionTags.toString());
        Assert.assertEquals(TagsUtil.merge(emptyTagValue, n1v1Tag).toString(), assertionTags.toString());

        Map<String, Set<String>> n1v1TagCopy = new HashMap<>();
        n1v1TagCopy = DatadogClientStub.addTagToMap(n1v1TagCopy, "name1", "value1");
        Assert.assertEquals(TagsUtil.merge(n1v1TagCopy, n1v1Tag).toString(), assertionTags.toString());

        Map<String, Set<String>> n1v2Tag = new HashMap<>();
        n1v2Tag = DatadogClientStub.addTagToMap(n1v2Tag, "name1", "value2");
        assertionTags = new HashMap<>();
        assertionTags = DatadogClientStub.addTagToMap(assertionTags, "name1", "value1");
        assertionTags = DatadogClientStub.addTagToMap(assertionTags, "name1", "value2");
        Assert.assertEquals(TagsUtil.merge(n1v2Tag, n1v1Tag).toString(), assertionTags.toString());

        Map<String, Set<String>> n2v1Tag = new HashMap<>();
        n2v1Tag = DatadogClientStub.addTagToMap(n2v1Tag, "name2", "value1");
        assertionTags = new HashMap<>();
        assertionTags = DatadogClientStub.addTagToMap(assertionTags, "name1", "value1");
        assertionTags = DatadogClientStub.addTagToMap(assertionTags, "name2", "value1");
        Assert.assertEquals(TagsUtil.merge(n2v1Tag, n1v1Tag).toString(), assertionTags.toString());

        n2v1Tag = new HashMap<>();
        n2v1Tag = DatadogClientStub.addTagToMap(n2v1Tag, "name2", "value1");
        Map<String, Set<String>> n2v1v2Tag = new HashMap<>();
        n2v1v2Tag = DatadogClientStub.addTagToMap(n2v1v2Tag, "name2", "value1");
        n2v1v2Tag = DatadogClientStub.addTagToMap(n2v1v2Tag, "name2", "value2");
        assertionTags = new HashMap<>();
        assertionTags = DatadogClientStub.addTagToMap(assertionTags, "name2", "value1");
        assertionTags = DatadogClientStub.addTagToMap(assertionTags, "name2", "value2");
        Assert.assertEquals(TagsUtil.merge(n2v1Tag, n2v1v2Tag) + " - " + assertionTags, TagsUtil.merge(n2v1Tag, n2v1v2Tag).toString(), assertionTags.toString());

    }

    @Test
    public void testTagsToMapSingleValues() {
        Map<String, String> emptyTags = TagsUtil.convertTagsToMapSingleValues(null);
        Assert.assertTrue(emptyTags.isEmpty());

        Map<String, Set<String>> singleValueTags = new HashMap<>();
        DatadogClientStub.addTagToMap(singleValueTags, "tagKey1", "tagValue1");
        DatadogClientStub.addTagToMap(singleValueTags, "tagKey2", "tagValue2");
        Map<String, String> resultSingleValues = TagsUtil.convertTagsToMapSingleValues(singleValueTags);
        Assert.assertEquals(2, resultSingleValues.size());
        Assert.assertEquals("tagValue1", resultSingleValues.get("tagKey1"));
        Assert.assertEquals("tagValue2", resultSingleValues.get("tagKey2"));

        Map<String, Set<String>> multipleValueTags = new HashMap<>();
        DatadogClientStub.addTagToMap(multipleValueTags, "tagKey1", "tagValue1");
        DatadogClientStub.addTagToMap(multipleValueTags, "tagKey2", "tagValue2_1");
        DatadogClientStub.addTagToMap(multipleValueTags, "tagKey2", "tagValue2_2");
        Map<String, String> resultMultipleValues = TagsUtil.convertTagsToMapSingleValues(multipleValueTags);
        Assert.assertEquals(2, resultMultipleValues.size());
        Assert.assertEquals("tagValue1", resultMultipleValues.get("tagKey1"));
        Assert.assertEquals("tagValue2_1", resultMultipleValues.get("tagKey2"));
    }

}
