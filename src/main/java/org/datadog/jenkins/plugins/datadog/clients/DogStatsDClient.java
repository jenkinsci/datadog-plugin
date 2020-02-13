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

import com.timgroup.statsd.*;
import hudson.util.Secret;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This class is used to collect all methods that has to do with transmitting
 * data to Datadog.
 */
public class DogStatsDClient implements DatadogClient {

    private static DatadogClient instance;
    private static final Logger logger = Logger.getLogger(DatadogHttpClient.class.getName());

    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL")
    public static boolean enableValidations = true;

    private StatsDClient statsd;
    private String hostname;
    private Integer port = null;
    private Integer logCollectionPort = null;
    private boolean isStopped = true;

    /**
     * NOTE: Use ClientFactory.getClient method to instantiate the client in the Jenkins Plugin
     * This method is not recommended to be used because it misses some validations.
     * @param hostname - target hostname
     * @param port - target port
     * @param logCollectionPort - target log collection port
     * @return an singleton instance of the DogStatsDClient.
     */
    @SuppressFBWarnings(value="DC_DOUBLECHECK")
    public static DatadogClient getInstance(String hostname, Integer port, Integer logCollectionPort){
        if(enableValidations){
            if (hostname == null || hostname.isEmpty()) {
                logger.severe("Datadog Target URL is not set properly");
                return null;
            }
            if (port == null) {
                logger.severe("Datadog Target Port is not set properly");
                return null;
            }
            if (logCollectionPort == null) {
                logger.warning("Datadog Log Collection Port is not set properly");
            }
        }

        if(instance == null){
            synchronized (DatadogHttpClient.class) {
                if(instance == null){
                    instance = new DogStatsDClient(hostname, port, logCollectionPort);
                }
            }
        }

        // We reset param just in case we change values
        if(!hostname.equals(((DogStatsDClient)instance).getHostname()) ||
                ((DogStatsDClient)instance).getPort() != port) {
            instance.setHostname(hostname);
            instance.setPort(port);
            ((DogStatsDClient)instance).reinitialize(true);
        }
        instance.setLogCollectionPort(logCollectionPort);
        return instance;
    }

    private DogStatsDClient(String hostname, Integer port, Integer logCollectionPort) {
        this.hostname = hostname;
        this.port = port;
        this.logCollectionPort = logCollectionPort;

        reinitialize(true);
    }

    /**
     * reinitialize the dogStasDClient
     * @param force - force to reinitialize
     * @return true if reinitialized properly otherwise false
     */
    private boolean reinitialize(boolean force) {
        try {
            if(!this.isStopped && this.statsd != null && !force){
                return true;
            }
            this.stop();
            logger.severe("Re/Initialize DogStatsD Client: hostname: " + this.hostname + " port = " + this.port);
            this.statsd = new NonBlockingStatsDClient(null, this.hostname, this.port);
            this.isStopped = false;
        } catch (Exception e){
            DatadogUtilities.severe(logger, e, "Failed to reinitialize DogStatsD Client: ");
            this.stop();
        }
        return !isStopped;
    }

    private boolean stop(){
        if (this.statsd != null){
            try{
                this.statsd.stop();
            }catch(Exception e){
                DatadogUtilities.severe(logger, e, "Failed to stop DogStatsD Client: ");
                return false;
            }
            this.statsd = null;
        }
        this.isStopped = true;
        return true;
    }

    public String getHostname() {
        return hostname;
    }

    @Override
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    @Override
    public void setPort(int port) {
        this.port = port;
    }

    public Integer getLogCollectionPort() {
        return logCollectionPort;
    }

    @Override
    public void setLogCollectionPort(Integer logCollectionPort) {
        this.logCollectionPort = logCollectionPort;
    }

    @Override
    public void setUrl(String url) {
        // noop
    }

    @Override
    public void setLogIntakeUrl(String logIntakeUrl) {
        // noop
    }

    @Override
    public void setApiKey(Secret apiKey){
        // noop
    }

    @Override
    public boolean isDefaultIntakeConnectionBroken() {
        return false;
    }

    @Override
    public void setDefaultIntakeConnectionBroken(boolean defaultIntakeConnectionBroken) {
        // noop
    }

    @Override
    public boolean isLogIntakeConnectionBroken() {
        return false;
    }

    @Override
    public void setLogIntakeConnectionBroken(boolean logIntakeConnectionBroken) {
        // noop
    }

    @Override
    public boolean event(DatadogEvent event) {
        try {
            reinitialize(false);
            logger.fine("Sending event");
            Event ev = Event.builder()
                    .withTitle(event.getTitle())
                    .withText(event.getText())
                    .withPriority(event.getPriority().toEventPriority())
                    .withHostname(event.getHost())
                    .withAlertType(event.getAlertType().toEventAlertType())
                    .withAggregationKey(event.getAggregationKey())
                    .withSourceTypeName("jenkins")
                    .build();
            this.statsd.recordEvent(ev, TagsUtil.convertTagsToArray(event.getTags()));
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, "An unexpected error occurred: ");
            reinitialize(true);
            return false;
        }
    }

    @Override
    public void incrementCounter(String name, String hostname, Map<String, Set<String>> tags) {
        try {
            reinitialize(false);
            logger.fine("increment counter with dogStatD client");
            this.statsd.incrementCounter(name, TagsUtil.convertTagsToArray(tags));
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, "An unexpected error occurred: ");
            reinitialize(true);
        }
    }

    @Override
    public void flushCounters() {
        return; //noop
    }

    @Override
    public boolean gauge(String name, long value, String hostname, Map<String, Set<String>> tags) {
        try {
            reinitialize(false);
            logger.fine("Submit gauge with dogStatD client");
            this.statsd.gauge(name, value, TagsUtil.convertTagsToArray(tags));
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, "An unexpected error occurred: ");
            reinitialize(true);
            return false;
        }
    }

    @Override
    public boolean serviceCheck(String name, Status status, String hostname, Map<String, Set<String>> tags) {
        try {
            reinitialize(false);
            logger.fine(String.format("Sending service check '%s' with status %s", name, status));

            ServiceCheck sc = ServiceCheck.builder()
                    .withName(name)
                    .withStatus(status.toServiceCheckStatus())
                    .withHostname(hostname)
                    .withTags(TagsUtil.convertTagsToArray(tags)).build();
            this.statsd.serviceCheck(sc);
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, "An unexpected error occurred: ");
            reinitialize(true);
            return false;
        }
    }

    @Override
    public boolean sendLogs(String payload) throws IOException {
        if(logCollectionPort == null){
            logger.severe("Datadog Log Collection Port is not set properly");
            throw new RuntimeException("Datadog Log Collection Port not set properly");
        }

        try (Socket socket = new Socket(hostname, logCollectionPort)) {

            OutputStream output = socket.getOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(output, "utf-8");
            writer.write(payload);

            InputStream input = socket.getInputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "utf-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();

            if ("{}".equals(result.toString())) {
                logger.fine(String.format("Logs API call was sent successfully!"));
                logger.fine(String.format("Payload: %s", payload));
                return true;
            } else {
                logger.severe(String.format("Logs API call failed!"));
                logger.severe(String.format("Payload: %s", payload));
                return false;
            }
        } catch (IOException e) {
            DatadogUtilities.severe(logger, e, "An unexpected error occurred: ");
            reinitialize(true);
            return false;
        }

    }

}
