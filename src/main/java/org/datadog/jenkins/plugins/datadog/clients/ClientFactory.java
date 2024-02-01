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

import hudson.util.Secret;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriterFactory;

public class ClientFactory {
    private static DatadogClient testClient;

    public static void setTestClient(DatadogClient testClient){
        // Only used for tests
        ClientFactory.testClient = testClient;
        TraceWriterFactory.onDatadogClientUpdate(testClient);
    }

    public static DatadogClient getClient(DatadogClient.ClientType type, String apiUrl, String logIntakeUrl,
                                          String webhookIntakeUrl, Secret apiKey, String host, Integer port,
                                          Integer logCollectionPort, Integer traceCollectionPort, String traceServiceName) {
        if(testClient != null){
            // Only used for tests
            return testClient;
        }
        switch(type){
            case HTTP:
                return DatadogApiClient.getInstance(apiUrl, logIntakeUrl, webhookIntakeUrl, apiKey);
            case DSD:
                return DatadogAgentClient.getInstance(host, port, logCollectionPort, traceCollectionPort);
            default:
                return null;
        }
    }

    public static DatadogClient getClient() {
        if(testClient != null){
            // Only used for tests
            return testClient;
        }

        DatadogGlobalConfiguration descriptor = DatadogUtilities.getDatadogGlobalDescriptor();
        if (descriptor == null) {
            return null;
        }

        String reportWith = descriptor.getReportWith();
        String targetApiURL = descriptor.getTargetApiURL();
        String targetLogIntakeURL = descriptor.getTargetLogIntakeURL();
        String targetWebhookIntakeURL = descriptor.getTargetWebhookIntakeURL();
        Secret targetApiKey = descriptor.getUsedApiKey();
        String targetHost = descriptor.getTargetHost();
        Integer targetPort = descriptor.getTargetPort();
        Integer targetLogCollectionPort = descriptor.getTargetLogCollectionPort();
        Integer targetTraceCollectionPort = descriptor.getTargetTraceCollectionPort();
        String ciInstanceName = descriptor.getCiInstanceName();
        return ClientFactory.getClient(DatadogClient.ClientType.valueOf(reportWith), targetApiURL, targetLogIntakeURL, targetWebhookIntakeURL,
                targetApiKey, targetHost, targetPort, targetLogCollectionPort, targetTraceCollectionPort, ciInstanceName);
    }
}
