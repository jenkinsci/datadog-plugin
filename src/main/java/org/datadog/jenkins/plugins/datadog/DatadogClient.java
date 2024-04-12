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

import com.timgroup.statsd.ServiceCheck;
import java.util.Map;
import java.util.Set;
import org.datadog.jenkins.plugins.datadog.metrics.MetricsClient;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategy;

public interface DatadogClient {

    enum Status {
        OK(0),
        WARNING(1),
        CRITICAL(2),
        UNKNOWN(3);

        private final int val;

        Status(int val) {
            this.val = val;
        }

        public int toValue(){
           return this.val;
        }

        public ServiceCheck.Status toServiceCheckStatus(){
            return ServiceCheck.Status.valueOf(this.name());
        }
    }

    /**
     * Sends an event to the Datadog API, including the event payload.
     *
     * @param event - a DatadogEvent object
     * @return a boolean to signify the success or failure of the HTTP POST request.
     */
    boolean event(DatadogEvent event);

    MetricsClient metrics();

    /**
     * Sends a service check to the Datadog API, including the check name, and status.
     *
     * @param name     - A String with the name of the service check to record.
     * @param status   - An Status with the status code to record for this service check.
     * @param hostname - A String with the hostname to submit.
     * @param tags     - A Map containing the tags to submit.
     * @return a boolean to signify the success or failure of the HTTP POST request.
     */
    boolean serviceCheck(String name, Status status, String hostname, Map<String, Set<String>> tags);

    /**
     * Send log message.
     * @param payload log payload to submit JSON object as String
     * @return a boolean to signify the success or failure of the request.
     */
    boolean sendLogs(String payload);

    TraceWriteStrategy createTraceWriteStrategy();

}
