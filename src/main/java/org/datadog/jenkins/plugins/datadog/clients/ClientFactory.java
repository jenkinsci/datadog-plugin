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

public class ClientFactory {
    private static DatadogClient testClient;

    public static void setTestClient(DatadogClient testClient){
        // Only used for tests
        ClientFactory.testClient = testClient;
    }

    public static DatadogClient getClient(DatadogClient.ClientType type, String apiUrl, String logIntakeUrl,
                                          Secret apiKey, String host, Integer port, Integer logCollectionPort,
                                          Integer traceCollectionPort, String traceServiceName){
        if(testClient != null){
            // Only used for tests
            return testClient;
        }
        switch(type){
            case HTTP:
                return DatadogHttpClient.getInstance(apiUrl, logIntakeUrl, apiKey);
            case DSD:
                return DogStatsDClient.getInstance(host, port, logCollectionPort, traceCollectionPort);
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
        String reportWith = null;
        String targetApiURL = null;
        String targetLogIntakeURL = null;
        Secret targetApiKey = null;
        String targetHost = null;
        Integer targetPort = null;
        Integer targetLogCollectionPort = null;
        Integer targetTraceCollectionPort = null;
        String ciInstanceName = null;
        if(descriptor != null){
            reportWith = descriptor.getReportWith();
            targetApiURL = descriptor.getTargetApiURL();
            targetLogIntakeURL = descriptor.getTargetLogIntakeURL();
            targetApiKey = descriptor.getTargetApiKey();
            targetHost = descriptor.getTargetHost();
            targetPort = descriptor.getTargetPort();
            targetLogCollectionPort = descriptor.getTargetLogCollectionPort();
            targetTraceCollectionPort = descriptor.getTargetTraceCollectionPort();
            ciInstanceName = descriptor.getCiInstanceName();
        }
        return ClientFactory.getClient(DatadogClient.ClientType.valueOf(reportWith), targetApiURL, targetLogIntakeURL,
                targetApiKey, targetHost, targetPort, targetLogCollectionPort, targetTraceCollectionPort, ciInstanceName);
    }
}
