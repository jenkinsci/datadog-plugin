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
import org.apache.commons.lang.StringUtils;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.logging.*;

/**
 * This class is used to collect all methods that has to do with transmitting
 * data to Datadog.
 */
public class DogStatsDClient implements DatadogClient {

    private static DogStatsDClient instance = null;
    // Used to determine if the instance failed last validation last time, so
    // we do not keep retrying to create the instance and logging the same error
    private static boolean failedLastValidation = false;

    private static final Logger logger = Logger.getLogger(DogStatsDClient.class.getName());

    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL")
    public static boolean enableValidations = true;

    private StatsDClient statsd;
    private Logger ddLogger;
    private String previousPayload;

    private String hostname = null;
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
    @SuppressFBWarnings(value={"DC_DOUBLECHECK", "RC_REF_COMPARISON"})
    public static DatadogClient getInstance(String hostname, Integer port, Integer logCollectionPort){
        // If the configuration has not changed, return the current instance without validation
        // since we've already validated and/or errored about the data

        DogStatsDClient newInstance = new DogStatsDClient(hostname, port, logCollectionPort);
        if (instance != null && instance.equals(newInstance)) {
            if (DogStatsDClient.failedLastValidation) {
                return null;
            }
            return instance;
        }

        synchronized (DogStatsDClient.class) {
            DogStatsDClient.instance = newInstance;
            if (enableValidations) {
                try {
                    newInstance.validateConfiguration();
                    DogStatsDClient.failedLastValidation = false;
                } catch(IllegalArgumentException e){
                    logger.severe(e.getMessage());
                    DogStatsDClient.failedLastValidation = true;
                    return null;
                }
            }
        }
        if (instance != null){
            instance.reinitialize(true);
            instance.reinitializeLogger(true);
        }
        return instance;
    }

    private DogStatsDClient(String hostname, Integer port, Integer logCollectionPort) {
        this.hostname = hostname;
        this.port = port;
        this.logCollectionPort = logCollectionPort;
    }

    public void validateConfiguration() throws IllegalArgumentException {
        if (hostname == null || hostname.isEmpty()) {
            throw new IllegalArgumentException("Datadog Target URL is not set properly");
        }
        if (port == null) {
            throw new IllegalArgumentException("Datadog Target Port is not set properly");
        }
        if (DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildLogs()  && logCollectionPort == null) {
            throw new IllegalArgumentException("Datadog Log Collection Port is not set properly");
        }
        return;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof DogStatsDClient)) {
            return false;
        }

        DogStatsDClient newInstance = (DogStatsDClient) object;

        if ((StringUtils.equals(getHostname(), newInstance.getHostname())
        && (((getPort() == null) && (newInstance.getPort() == null)) || (null != getPort() && port.equals(newInstance.getPort())))
        && (((getLogCollectionPort() == null) && (newInstance.getLogCollectionPort() == null)) || (null != getLogCollectionPort() && logCollectionPort.equals(newInstance.getLogCollectionPort()))))){
           return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = hostname != null ? hostname.hashCode() : 0;
        result = 47 * result + (port != null ? port.hashCode() : 0);
        result = 47 * result + (logCollectionPort != null ? logCollectionPort.hashCode() : 0);
        return result;
    }

    /**
     * reinitialize the dogStatsD Client
     * @param force - force to reinitialize
     * @return true if reinitialized properly otherwise false
     */
    private boolean reinitialize(boolean force) {
        try {
            if(!this.isStopped && this.statsd != null && !force){
                return true;
            }
            this.stop();
            logger.info("Re/Initialize DogStatsD Client: hostname = " + this.hostname + ", port = " + this.port);
            this.statsd = new NonBlockingStatsDClient(null, this.hostname, this.port);
            this.isStopped = false;
        } catch (Exception e){
            DatadogUtilities.severe(logger, e, "Failed to reinitialize DogStatsD Client");
            this.stop();
        }
        return !isStopped;
    }

    /**
     * reinitialize the Logger Client
     * @param force - force to reinitialize
     * @return true if reinitialized properly otherwise false
     */
    private boolean reinitializeLogger(boolean force) {
        if(this.ddLogger != null && !force){
            return true;
        }
        if(!DatadogUtilities.getDatadogGlobalDescriptor().isCollectBuildLogs() || this.logCollectionPort == null){
            return false;
        }
        try {
            logger.info("Re/Initialize Datadog-Plugin Logger: hostname = " + this.hostname + ", logCollectionPort = " + this.logCollectionPort);
            this.ddLogger = Logger.getLogger("Datadog-Plugin Logger");
            this.ddLogger.setUseParentHandlers(false);
            //Remove all existing Handlers
            Handler[] handlers = this.ddLogger.getHandlers();
            for(Handler h : handlers){
                this.ddLogger.removeHandler(h);
            }
            //Add New Handler
            SocketHandler socketHandler = new SocketHandler(this.hostname, this.logCollectionPort);
            socketHandler.setFormatter(new DatadogFormatter());
            socketHandler.setErrorManager(new DatadogErrorManager());
            this.ddLogger.addHandler(socketHandler);
        } catch (Exception e){
            if(e instanceof UnknownHostException){
                DatadogUtilities.severe(logger, e, "Failed to reinitialize Datadog-Plugin Logger, Unknown Host " + this.hostname);
            }else if(e instanceof ConnectException){
                DatadogUtilities.severe(logger, e, "Failed to reinitialize Datadog-Plugin Logger, Connection exception. This may be because your port is incorrect " + this.logCollectionPort);
            }else{
                DatadogUtilities.severe(logger, e, "Failed to reinitialize Datadog-Plugin Logger");
            }
            return false;
        }
        return true;
    }

    private boolean stop(){
        if (this.statsd != null){
            try{
                this.statsd.stop();
            }catch(Exception e){
                DatadogUtilities.severe(logger, e, "Failed to stop DogStatsD Client");
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

    public Integer getPort() {
        return port;
    }

    @Override
    public void setPort(Integer port) {
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
            boolean status = reinitialize(false);
            if(!status){
                return false;
            }
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
            DatadogUtilities.severe(logger, e, null);
            reinitialize(true);
            return false;
        }
    }

    @Override
    public boolean incrementCounter(String name, String hostname, Map<String, Set<String>> tags) {
        try {
            boolean status = reinitialize(false);
            if(!status){
                return false;
            }
            logger.fine("increment counter with dogStatD client");
            this.statsd.incrementCounter(name, TagsUtil.convertTagsToArray(tags));
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, null);
            reinitialize(true);
            return false;
        }
    }

    @Override
    public void flushCounters() {
        return; //noop
    }

    @Override
    public boolean gauge(String name, long value, String hostname, Map<String, Set<String>> tags) {
        try {
            boolean status = reinitialize(false);
            if(!status){
                return false;
            }
            logger.fine("Submit gauge with dogStatD client");
            this.statsd.gauge(name, value, TagsUtil.convertTagsToArray(tags));
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, null);
            reinitialize(true);
            return false;
        }
    }

    @Override
    public boolean serviceCheck(String name, Status status, String hostname, Map<String, Set<String>> tags) {
        try {
            boolean initStatus = reinitialize(false);
            if(!initStatus){
                return false;
            }
            logger.fine(String.format("Sending service check '%s' with status %s", name, status));

            ServiceCheck sc = ServiceCheck.builder()
                    .withName(name)
                    .withStatus(status.toServiceCheckStatus())
                    .withHostname(hostname)
                    .withTags(TagsUtil.convertTagsToArray(tags)).build();
            this.statsd.serviceCheck(sc);
            return true;
        } catch(Exception e){
            DatadogUtilities.severe(logger, e, null);
            reinitialize(true);
            return false;
        }
    }

    @Override
    public boolean sendLogs(String payload) {
        if(logCollectionPort == null){
            logger.severe("Datadog Log Collection Port is not set properly");
            return false;
        }

        if(this.ddLogger == null) {
            boolean status = reinitializeLogger(true);
            if(!status) {
                return false;
            }
        }
        // Check if we have handlers in our logger. This may happen when ddLogger initialization fails
        // ddLogger may not be null but may be mis-configured.
        // Reset to null to reinitialize if needed.
        if(this.ddLogger.getHandlers().length == 0){
            this.ddLogger = null;
            return false;
        }

        try {
            this.ddLogger.info(payload);

            // We check for errors in our custom errorManager
            Handler handler = this.ddLogger.getHandlers()[0];
            DatadogErrorManager errorManager = (DatadogErrorManager)handler.getErrorManager();
            if(errorManager.hadReportedIssue()){
                reinitializeLogger(true);
                // NOTE: After a socket timeout, the first message to be sent get lost, it is only the second message
                // that gets reported as an error in the errorManager.
                // For this reason, we always keep the previousPayload in order to resubmit it.
                this.ddLogger.info(previousPayload);
                previousPayload = payload;
                // we return false so that we retry to send the current payload message that still didn't get submitted.
                return false;
            }
            previousPayload = payload;
        }catch(Exception e){
            DatadogUtilities.severe(logger, e, null);
            reinitialize(true);
            previousPayload = payload;
            return false;
        }
        return true;
    }
}
