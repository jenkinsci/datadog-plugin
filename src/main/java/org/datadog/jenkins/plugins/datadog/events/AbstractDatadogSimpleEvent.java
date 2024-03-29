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

package org.datadog.jenkins.plugins.datadog.events;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

import java.util.Map;
import java.util.Set;

public abstract class AbstractDatadogSimpleEvent extends AbstractDatadogEvent {

    private boolean isTemporarily;

    public AbstractDatadogSimpleEvent(Map<String, Set<String>> tags) {
        setHost(DatadogUtilities.getHostname(null));
        setJenkinsUrl(DatadogUtilities.getJenkinsUrl());
        setDate(DatadogUtilities.currentTimeMillis() / 1000);
        setTags(TagsUtil.merge(TagsUtil.addTagToTags(null, "event_type", SYSTEM_EVENT_TYPE), tags));
    }

    public boolean isTemporarily() {
        return this.isTemporarily;
    }

    public void setIsTemporarily(boolean isTemporarily) {
        this.isTemporarily = isTemporarily;
    }

}
