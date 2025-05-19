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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.sf.json.JSONArray;

public class TagsUtil {

    private static final Logger LOGGER = Logger.getLogger(TagsUtil.class.getName());

    public static Map<String, Set<String>> merge(Map<String, Set<String>> dest, Map<String, Set<String>> orig) {
        if (dest == null) {
            dest = new HashMap<>();
        }
        if (orig != null) {
            for (Map.Entry<String, Set<String>> entry : orig.entrySet()) {
                final String oName = entry.getKey();
                Set<String> oValues = entry.getValue();
                Set<String> dValues = dest.computeIfAbsent(oName, k -> new LinkedHashSet<>());
                if (oValues != null) {
                    dValues.addAll(oValues);
                }
            }
        }
        return dest;
    }

    public static JSONArray convertTagsToJSONArray(Map<String, Set<String>> tags){
        JSONArray result = new JSONArray();
        for (Map.Entry<String, Set<String>> entry : tags.entrySet()) {
            String name = entry.getKey();
            Set<String> values = entry.getValue();
            for (String value : values) {
                if ("".equals(value) || value == null) {
                    result.add(name); // Tag with no value
                } else {
                    result.add(String.format("%s:%s", name, value));
                }
            }
        }
        return result;
    }

    public static String[] convertTagsToArray(Map<String, Set<String>> tags){
        if (tags == null) {
            return new String[0];
        }

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : tags.entrySet()) {
            String name = entry.getKey();
            Set<String> values = entry.getValue();
            for (String value : values){
                if("".equals(value) || value == null){
                    result.add(name);
                }else{
                    result.add(String.format("%s:%s", name, value));
                }
            }
        }

        Collections.sort(result);
        return result.toArray(new String[0]);
    }

    public static Map<String,Set<String>> addTagToTags(Map<String, Set<String>> tags, String name, String value) {
        if(tags == null){
            tags = new HashMap<>();
        }
        tags.computeIfAbsent(name, k -> new HashSet<>()).add(value);
        return tags;
    }

    public static Map<String, String> convertTagsToMapSingleValues(Map<String, Set<String>> tags) {
        if(tags == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : tags.entrySet()) {
            Set<String> tagValues = entry.getValue();
            if (tagValues == null || tagValues.isEmpty()) {
                continue;
            }

            String tagName = entry.getKey();
            String innermostTagValue = tagValues.iterator().next();
            if (tagValues.size() > 1) {
                LOGGER.warning("Unsupported multi-value tag in this context: '"+ tagName + "' - the value '" + innermostTagValue + "' from the innermost context will be used (all values: " + tagValues + ")");
            }
            result.put(tagName, innermostTagValue);
        }

        return result;
    }
}
