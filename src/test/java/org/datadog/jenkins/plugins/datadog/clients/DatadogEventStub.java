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

package org.datadog.jenkins.plugins.datadog.clients;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;

public class DatadogEventStub {
    private String title;
    private String text;
    private String host;
    private String jenkinsUrl;
    private DatadogEvent.Priority priority;
    private DatadogEvent.AlertType alertType;
    private Long date;
    private Map<String, Set<String>> tags;

    DatadogEventStub(DatadogEvent event) {
        this.title = event.getTitle();
        this.text = event.getText();
        this.host = event.getHost();
        this.jenkinsUrl = event.getJenkinsUrl();
        this.priority = event.getPriority();
        this.alertType = event.getAlertType();
        this.date = event.getDate();
        this.tags = event.getTags();
    }

    public Map<String, Set<String>> getTags(){
        return this.tags;
    }

    /**
     * Check if the event is close-enough by comparing subset of values
     */
    public boolean same(String title, DatadogEvent.Priority priority, DatadogEvent.AlertType alertType, Long date) {
        return Objects.equals(this.title, title) &&
                this.priority == priority &&
                this.alertType == alertType &&
                Objects.equals(this.date, date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DatadogEventStub that = (DatadogEventStub) o;
        return Objects.equals(title, that.title) &&
                Objects.equals(text, that.text) &&
                Objects.equals(host, that.host) &&
                Objects.equals(jenkinsUrl, that.jenkinsUrl) &&
                priority == that.priority &&
                alertType == that.alertType &&
                Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, text, host, jenkinsUrl, priority, alertType, date, tags);
    }

    @Override
    public String toString() {
        return "DatadogEventStub{" +
                "title='" + title + '\'' +
                ", priority=" + priority +
                ", alertType=" + alertType +
                ", date=" + date +
                ", tags=" + tags +
                '}';
    }
}
