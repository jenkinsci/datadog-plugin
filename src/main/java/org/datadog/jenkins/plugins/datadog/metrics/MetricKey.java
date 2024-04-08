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

package org.datadog.jenkins.plugins.datadog.metrics;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MetricKey {

    private final Map<String, Set<String>> tags;
    private final String metricName;
    private final String hostname;

    public MetricKey(Map<String, Set<String>> tags, String metricName, String hostname) {
        this.tags = tags;
        this.metricName = metricName;
        this.hostname = hostname;
    }

    public Map<String, Set<String>> getTags() {
        return tags;
    }

    public String getMetricName() {
        return metricName;
    }

    public String getHostname() {
        return hostname;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MetricKey that = (MetricKey) o;
        return Objects.equals(tags, that.tags)
                && Objects.equals(metricName, that.metricName)
                && Objects.equals(hostname, that.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags, metricName, hostname);
    }

    @Override
    public String toString() {
        return "MetricKey{" +
                "tags=" + tags +
                ", metricName='" + metricName + '\'' +
                ", hostname='" + hostname + '\'' +
                '}';
    }
}
