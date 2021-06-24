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

import static hudson.Util.fixEmptyAndTrim;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogHttpClient;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;

@Extension
public class DatadogGlobalConfiguration extends GlobalConfiguration {

    private static final Logger logger = Logger.getLogger(DatadogGlobalConfiguration.class.getName());
    private static final String DISPLAY_NAME = "Datadog Plugin";


    private static String REPORT_WITH_PROPERTY = "DATADOG_JENKINS_PLUGIN_REPORT_WITH";
    private static String TARGET_API_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_URL";
    private static String TARGET_LOG_INTAKE_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_LOG_INTAKE_URL";
    private static String TARGET_API_KEY_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_KEY";
    private static String TARGET_HOST_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_HOST";
    private static String TARGET_PORT_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_PORT";
    private static String TARGET_LOG_COLLECTION_PORT_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_LOG_COLLECTION_PORT";
    private static String TARGET_TRACE_COLLECTION_PORT_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_TRACE_COLLECTION_PORT";
    private static final String TARGET_TRACE_SERVICE_NAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_TRACE_SERVICE_NAME";
    private static String HOSTNAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_HOSTNAME";
    private static String EXCLUDED_PROPERTY = "DATADOG_JENKINS_PLUGIN_EXCLUDED";
    private static String INCLUDED_PROPERTY = "DATADOG_JENKINS_PLUGIN_INCLUDED";
    //Deprecated
    private static String BLACKLIST_PROPERTY = "DATADOG_JENKINS_PLUGIN_BLACKLIST";
    private static String WHITELIST_PROPERTY = "DATADOG_JENKINS_PLUGIN_WHITELIST";
    
    private static String GLOBAL_TAG_FILE_PROPERTY = "DATADOG_JENKINS_PLUGIN_GLOBAL_TAG_FILE";
    private static String GLOBAL_TAGS_PROPERTY = "DATADOG_JENKINS_PLUGIN_GLOBAL_TAGS";
    private static String GLOBAL_JOB_TAGS_PROPERTY = "DATADOG_JENKINS_PLUGIN_GLOBAL_JOB_TAGS";
    private static String EMIT_SECURITY_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_EMIT_SECURITY_EVENTS";
    private static String EMIT_SYSTEM_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_EMIT_SYSTEM_EVENTS";
    private static String EMIT_CONFIG_CHANGE_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_EMIT_CONFIG_CHANGE_EVENTS";
    private static String COLLECT_BUILD_LOGS_PROPERTY = "DATADOG_JENKINS_PLUGIN_COLLECT_BUILD_LOGS";

    private static String ENABLE_CI_VISIBILITY_PROPERTY = "DATADOG_JENKINS_PLUGIN_ENABLE_CI_VISIBILITY";
    private static String CI_VISIBILITY_CI_INSTANCE_NAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_CI_VISIBILITY_CI_INSTANCE_NAME";

    private static String DEFAULT_REPORT_WITH_VALUE = DatadogClient.ClientType.HTTP.name();
    private static String DEFAULT_TARGET_API_URL_VALUE = "https://api.datadoghq.com/api/";
    private static String DEFAULT_TARGET_LOG_INTAKE_URL_VALUE = "https://http-intake.logs.datadoghq.com/v1/input/";
    private static String DEFAULT_TARGET_HOST_VALUE = "localhost";
    private static Integer DEFAULT_TARGET_PORT_VALUE = 8125;
    private static Integer DEFAULT_TRACES_PORT_VALUE = 8126;
    private static String DEFAULT_CI_INSTANCE_NAME = "jenkins";
    private static Integer DEFAULT_TARGET_LOG_COLLECTION_PORT_VALUE = null;
    private static boolean DEFAULT_EMIT_SECURITY_EVENTS_VALUE = true;
    private static boolean DEFAULT_EMIT_SYSTEM_EVENTS_VALUE = true;
    private static boolean DEFAULT_EMIT_CONFIG_CHANGE_EVENTS_VALUE = false;
    private static boolean DEFAULT_COLLECT_BUILD_LOGS_VALUE = false;
    private static boolean DEFAULT_COLLECT_BUILD_TRACES_VALUE = false;

    private String reportWith = DEFAULT_REPORT_WITH_VALUE;
    private String targetApiURL = DEFAULT_TARGET_API_URL_VALUE;
    private String targetLogIntakeURL = DEFAULT_TARGET_LOG_INTAKE_URL_VALUE;
    private Secret targetApiKey = null;
    private String targetHost = DEFAULT_TARGET_HOST_VALUE;
    private Integer targetPort = DEFAULT_TARGET_PORT_VALUE;
    private Integer targetLogCollectionPort = DEFAULT_TARGET_LOG_COLLECTION_PORT_VALUE;
    private Integer targetTraceCollectionPort = DEFAULT_TRACES_PORT_VALUE;
    private String traceServiceName = DEFAULT_CI_INSTANCE_NAME;
    private String hostname = null;
    private String blacklist = null;
    private String whitelist = null;
    private String globalTagFile = null;
    private String globalTags = null;
    private String globalJobTags = null;
    private boolean emitSecurityEvents = DEFAULT_EMIT_SECURITY_EVENTS_VALUE;
    private boolean emitSystemEvents = DEFAULT_EMIT_SYSTEM_EVENTS_VALUE;
    private boolean emitConfigChangeEvents = DEFAULT_EMIT_CONFIG_CHANGE_EVENTS_VALUE;
    private boolean collectBuildLogs = DEFAULT_COLLECT_BUILD_LOGS_VALUE;
    private boolean collectBuildTraces = DEFAULT_COLLECT_BUILD_TRACES_VALUE;

    @DataBoundConstructor
    public DatadogGlobalConfiguration() {
        load(); // Load the persisted global configuration
        loadEnvVariables(); // Load environment variables after as they should take precedence.
    }

    private void loadEnvVariables(){
        String reportWithEnvVar = System.getenv(REPORT_WITH_PROPERTY);
        if(StringUtils.isNotBlank(reportWithEnvVar) &&
                (reportWithEnvVar.equals(DatadogClient.ClientType.HTTP.name()) ||
                        reportWithEnvVar.equals(DatadogClient.ClientType.DSD.name()))){
            this.reportWith = reportWithEnvVar;
        }

        String targetApiURLEnvVar = System.getenv(TARGET_API_URL_PROPERTY);
        if(StringUtils.isNotBlank(targetApiURLEnvVar)){
            this.targetApiURL = targetApiURLEnvVar;
        }

        String targetLogIntakeURLEnvVar = System.getenv(TARGET_LOG_INTAKE_URL_PROPERTY);
        if(StringUtils.isNotBlank(targetApiURLEnvVar)){
            this.targetLogIntakeURL = targetLogIntakeURLEnvVar;
        }

        String targetApiKeyEnvVar = System.getenv(TARGET_API_KEY_PROPERTY);
        if(StringUtils.isNotBlank(targetApiKeyEnvVar)){
            this.targetApiKey = Secret.fromString(targetApiKeyEnvVar);
        }

        String targetHostEnvVar = System.getenv(TARGET_HOST_PROPERTY);
        if(StringUtils.isNotBlank(targetHostEnvVar)){
            this.targetHost = targetHostEnvVar;
        }

        String targetPortEnvVar = System.getenv(TARGET_PORT_PROPERTY);
        if(StringUtils.isNotBlank(targetPortEnvVar) && StringUtils.isNumeric(targetPortEnvVar)){
            this.targetPort = Integer.valueOf(targetPortEnvVar);
        }

        String targetLogCollectionPortEnvVar = System.getenv(TARGET_LOG_COLLECTION_PORT_PROPERTY);
        if(StringUtils.isNotBlank(targetLogCollectionPortEnvVar) && StringUtils.isNumeric(targetLogCollectionPortEnvVar)){
            this.targetLogCollectionPort = Integer.valueOf(targetLogCollectionPortEnvVar);
        }

        String targetTraceCollectionPortEnvVar = System.getenv(TARGET_TRACE_COLLECTION_PORT_PROPERTY);
        if(StringUtils.isNotBlank(targetTraceCollectionPortEnvVar) && StringUtils.isNumeric(targetTraceCollectionPortEnvVar)) {
            this.targetTraceCollectionPort = Integer.valueOf(targetTraceCollectionPortEnvVar);
        }

        String traceServiceNameVar = System.getenv(TARGET_TRACE_SERVICE_NAME_PROPERTY);
        if(StringUtils.isNotBlank(targetApiKeyEnvVar)) {
            this.traceServiceName = traceServiceNameVar;
        }

        String hostnameEnvVar = System.getenv(HOSTNAME_PROPERTY);
        if(StringUtils.isNotBlank(hostnameEnvVar)){
            this.hostname = hostnameEnvVar;
        }
        
        String excludedEnvVar = System.getenv(EXCLUDED_PROPERTY);
        if(StringUtils.isBlank(excludedEnvVar)){
            // backwards compatibility
            excludedEnvVar = System.getenv(BLACKLIST_PROPERTY);
            if(StringUtils.isNotBlank(excludedEnvVar)){
                this.blacklist = excludedEnvVar;
            }
        } else {
            this.blacklist = excludedEnvVar;
        }
        
        String includedEnvVar = System.getenv(INCLUDED_PROPERTY);
        if(StringUtils.isBlank(includedEnvVar)){
            // backwards compatibility
            includedEnvVar = System.getenv(WHITELIST_PROPERTY);
            if(StringUtils.isNotBlank(includedEnvVar)){
                this.whitelist = includedEnvVar;
            }    
        } else {
            this.whitelist = includedEnvVar;
        }

        String globalTagFileEnvVar = System.getenv(GLOBAL_TAG_FILE_PROPERTY);
        if(StringUtils.isNotBlank(globalTagFileEnvVar)){
            this.globalTagFile = globalTagFileEnvVar;
        }

        String globalTagsEnvVar = System.getenv(GLOBAL_TAGS_PROPERTY);
        if(StringUtils.isNotBlank(globalTagsEnvVar)){
            this.globalTags = globalTagsEnvVar;
        }

        String globalJobTagsEnvVar = System.getenv(GLOBAL_JOB_TAGS_PROPERTY);
        if(StringUtils.isNotBlank(globalJobTagsEnvVar)){
            this.globalJobTags = globalJobTagsEnvVar;
        }

        String emitSecurityEventsEnvVar = System.getenv(EMIT_SECURITY_EVENTS_PROPERTY);
        if(StringUtils.isNotBlank(emitSecurityEventsEnvVar)){
            this.emitSecurityEvents = Boolean.valueOf(emitSecurityEventsEnvVar);
        }

        String emitSystemEventsEnvVar = System.getenv(EMIT_SYSTEM_EVENTS_PROPERTY);
        if(StringUtils.isNotBlank(emitSystemEventsEnvVar)){
            this.emitSystemEvents = Boolean.valueOf(emitSystemEventsEnvVar);
        }

        String emitConfigChangeEventsEnvVar = System.getenv(EMIT_CONFIG_CHANGE_EVENTS_PROPERTY);
        if(StringUtils.isNotBlank(emitConfigChangeEventsEnvVar)){
            this.emitConfigChangeEvents = Boolean.valueOf(emitConfigChangeEventsEnvVar);
        }

        String collectBuildLogsEnvVar = System.getenv(COLLECT_BUILD_LOGS_PROPERTY);
        if(StringUtils.isNotBlank(collectBuildLogsEnvVar)){
            this.collectBuildLogs = Boolean.valueOf(collectBuildLogsEnvVar);
        }

        String enableCiVisibilityVar = System.getenv(ENABLE_CI_VISIBILITY_PROPERTY);
        if(StringUtils.isNotBlank(enableCiVisibilityVar)){
            final boolean enableCiVisibility = Boolean.valueOf(enableCiVisibilityVar);
            if(enableCiVisibility && DatadogClient.ClientType.HTTP.name().equals(this.reportWith)) {
                logger.warning("CI Visibility can only be enabled using Datadog Agent mode.");
            } else {
                this.collectBuildTraces = enableCiVisibility;
            }
        }

        String ciVisibilityCiInstanceNameVar = System.getenv(CI_VISIBILITY_CI_INSTANCE_NAME_PROPERTY);
        if(StringUtils.isNotBlank(ciVisibilityCiInstanceNameVar)) {
            this.traceServiceName = ciVisibilityCiInstanceNameVar;
        }
    }

    /**
     * Tests the apiKey field from the configuration screen, to check its' validity.
     * It is used in the config.jelly resource file. See method="testConnection"
     *
     * @param targetApiKey - A String containing the apiKey submitted from the form on the
     *                   configuration screen, which will be used to authenticate a request to the
     *                   Datadog API.
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     * @throws IOException      if there is an input/output exception.
     * @throws ServletException if there is a servlet exception.
     */
    @RequirePOST
    public FormValidation doTestConnection(@QueryParameter("targetApiKey") final String targetApiKey, @QueryParameter("targetApiURL") final String targetApiURL)
            throws IOException, ServletException {
        if (DatadogHttpClient.validateDefaultIntakeConnection(targetApiURL, Secret.fromString(targetApiKey))) {
            return FormValidation.ok("Great! Your API key is valid.");
        } else {
            return FormValidation.error("Hmmm, your API key seems to be invalid.");
        }
    }

    /**
     * Tests the hostname field from the configuration screen, to determine if
     * the hostname is of a valid format, according to the RFC 1123.
     * It is used in the config.jelly resource file. See method="testHostname"
     *
     * @param hostname - A String containing the hostname submitted from the form on the
     *                     configuration screen, which will be used to authenticate a request to the
     *                     Datadog API.
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    @RequirePOST
    public FormValidation doTestHostname(@QueryParameter("hostname") final String hostname){
        if(StringUtils.isNotBlank(hostname) && DatadogUtilities.isValidHostname(hostname)) {
            return FormValidation.ok("Great! Your hostname is valid.");
        } else {
            return FormValidation.error("Your hostname is invalid, likely because it violates the format set in RFC 1123");
        }
    }

    /**
     * @param targetApiURL - The API URL which the plugin will report to.
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    @RequirePOST
    public FormValidation doCheckTargetApiURL(@QueryParameter("targetApiURL") final String targetApiURL) {
        if(!validateURL(targetApiURL)) {
            return FormValidation.error("The field must be configured in the form <http|https>://<url>/");
        }

        return FormValidation.ok();
    }

    /**
     * @param targetLogIntakeURL - The Log Intake URL which the plugin will report to.
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    @RequirePOST
    public FormValidation doCheckTargetLogIntakeURL(@QueryParameter("targetLogIntakeURL") final String targetLogIntakeURL) {
        if (!validateURL(targetLogIntakeURL) && collectBuildLogs) {
            return FormValidation.error("The field must be configured in the form <http|https>://<url>/");
        }

        return FormValidation.ok();
    }

    private boolean validateTargetHost(String targetHost) {
        if(!DatadogClient.ClientType.DSD.name().equals(reportWith)) {
            return true;
        }

        return StringUtils.isNotBlank(targetHost);
    }

    public static boolean validateURL(String targetURL) {
        return StringUtils.isNotBlank(targetURL) && targetURL.contains("http");
    }

    /**
     * @param targetHost - The dogStatsD Host which the plugin will report to.
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    @RequirePOST
    public FormValidation doCheckTargetHost(@QueryParameter("targetHost") final String targetHost) {
        if (!validateTargetHost(targetHost)) {
            return FormValidation.error("Invalid Host");
        }

        return FormValidation.ok();
    }

    public static boolean validatePort(String targetPort) {
        return StringUtils.isNotBlank(targetPort) && StringUtils.isNumeric(targetPort) && NumberUtils.createInteger(targetPort) != 0;
    }

    /**
     * @param targetPort - The dogStatsD Port which the plugin will report to.
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    @RequirePOST
    public FormValidation doCheckTargetPort(@QueryParameter("targetPort") final String targetPort) {
        if (!validatePort(targetPort)) {
            return FormValidation.error("Invalid Port");
        }

        return FormValidation.ok();
    }

    /**
     * @param targetLogCollectionPort - The Log Collection Port which the plugin will report to.
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    @RequirePOST
    public FormValidation doCheckTargetLogCollectionPort(@QueryParameter("targetLogCollectionPort") final String targetLogCollectionPort) {
        if (!validatePort(targetLogCollectionPort) && collectBuildLogs) {
            return FormValidation.error("Invalid Log Collection Port");
        }

        return FormValidation.ok();
    }

    /**
     * @param targetTraceCollectionPort - The Trace Collection Port which the plugin will report to.
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    @RequirePOST
    public FormValidation doCheckTargetTraceCollectionPort(@QueryParameter("targetTraceCollectionPort") final String targetTraceCollectionPort) {
        if (!validatePort(targetTraceCollectionPort) && collectBuildTraces) {
            return FormValidation.error("Invalid Trace Collection Port");
        }

        return FormValidation.ok();
    }

    @RequirePOST
    public FormValidation doCheckTraceServiceName(@QueryParameter("traceServiceName") final String traceServiceName) {
        if(StringUtils.isBlank(traceServiceName) && collectBuildTraces){
            return FormValidation.error("Invalid CI Instance Name");
        }
        return FormValidation.ok();
    }

    /**
     * Indicates if this builder can be used with all kinds of project types.
     *
     * @param aClass - An extension of the AbstractProject class representing a specific type of
     *               project.
     * @return a boolean signifying whether or not a builder can be used with a specific type of
     * project.
     */
    public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
        return true;
    }

    /**
     * Getter function for a human readable plugin name, used in the configuration screen.
     *
     * @return a String containing the human readable display name for this plugin.
     */
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    /**
     * Indicates if this builder can be used with all kinds of project types.
     *
     * @param req      - A StaplerRequest object
     * @param formData - A JSONObject containing the submitted form data from the configuration
     *                 screen.
     * @return a boolean signifying the success or failure of configuration.
     * @throws FormException if the formData is invalid.
     */
    @Override
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {
        try {

            if(!super.configure(req, formData)){
                return false;
            }

            final String reportWith = formData.getString("reportWith");
            this.setReportWith(reportWith);
            this.setTargetApiURL(formData.getString("targetApiURL"));
            this.setTargetLogIntakeURL(formData.getString("targetLogIntakeURL"));
            this.setTargetApiKey(formData.getString("targetApiKey"));
            this.setTargetHost(formData.getString("targetHost"));
            String portStr = formData.getString("targetPort");
            if (validatePort(portStr)) {
                this.setTargetPort(formData.getInt("targetPort"));
            } else {
                this.setTargetPort(null);
            }
            String logCollectionPortStr = formData.getString("targetLogCollectionPort");
            if(validatePort(logCollectionPortStr)){
                this.setTargetLogCollectionPort(formData.getInt("targetLogCollectionPort"));
            }else{
                this.setTargetLogCollectionPort(null);
            }

            final String traceCollectionPortStr = formData.getString("targetTraceCollectionPort");
            if(validatePort(traceCollectionPortStr)){
                this.setTargetTraceCollectionPort(formData.getInt("targetTraceCollectionPort"));
            }else{
                this.setTargetTraceCollectionPort(DEFAULT_TRACES_PORT_VALUE);
            }

            try {
                final JSONObject ciVisibilityData = formData.getJSONObject("ciVisibilityData");
                if (ciVisibilityData != null && !ciVisibilityData.isNullObject()) {
                    if (!"DSD".equalsIgnoreCase(reportWith)) {
                        throw new FormException("CI Visibility can only be enabled using Datadog Agent mode.", "collectBuildTraces");
                    }

                    final String ciInstanceName = ciVisibilityData.getString("traceServiceName");
                    if (StringUtils.isNotBlank(ciInstanceName)) {
                        this.setCiInstanceName(ciInstanceName);
                    } else {
                        this.setCiInstanceName(DEFAULT_CI_INSTANCE_NAME);
                    }
                }
                this.setEnableCiVisibility(ciVisibilityData != null && !ciVisibilityData.isNullObject());

            } catch (FormException ex) {
                //If it is the validation exception, we throw it to the next level.
                throw ex;
            } catch (Exception ex) {
                // We disable CI Visibility if there is an error parsing the CI Visibility configuration
                // because we don't want to prevent the user process the rest of the configuration.
                this.setEnableCiVisibility(false);
                this.setCiInstanceName(DEFAULT_CI_INSTANCE_NAME);
                DatadogUtilities.severe(logger, ex, "Failed to configure CI Visibility: " + ex.getMessage());
            }

            if(StringUtils.isNotBlank(this.getHostname()) && !DatadogUtilities.isValidHostname(this.getHostname())){
                throw new FormException("Your hostname is invalid, likely because it violates the format set in RFC 1123", "hostname");
            }
            
            this.setHostname(formData.getString("hostname"));
            // These config names have to be kept for backwards compatibility reasons
            this.setExcluded(formData.getString("blacklist"));
            this.setIncluded(formData.getString("whitelist"));          
            this.setGlobalTagFile(formData.getString("globalTagFile"));
            this.setGlobalTags(formData.getString("globalTags"));
            this.setGlobalJobTags(formData.getString("globalJobTags"));
            this.setEmitSecurityEvents(formData.getBoolean("emitSecurityEvents"));
            this.setEmitSystemEvents(formData.getBoolean("emitSystemEvents"));
            this.setEmitConfigChangeEvents(formData.getBoolean("emitConfigChangeEvents"));
            this.setCollectBuildLogs(formData.getBoolean("collectBuildLogs"));

            //When form is saved....
            DatadogClient client = ClientFactory.getClient(DatadogClient.ClientType.valueOf(this.getReportWith()),
                    this.getTargetApiURL(), this.getTargetLogIntakeURL(), this.getTargetApiKey(), this.getTargetHost(),
                    this.getTargetPort(), this.getTargetLogCollectionPort(), this.getTargetTraceCollectionPort(), this.getCiInstanceName());
                // ...reinitialize the DatadogClient
            if(client == null) {
                return false;
            }
            client.setDefaultIntakeConnectionBroken(false);
            client.setLogIntakeConnectionBroken(false);
            // Persist global configuration information
            save();
            return true;
        }catch(Exception e){
            // Intercept all FormException instances.
            if(e instanceof FormException){
                throw (FormException)e;
            }

            DatadogUtilities.severe(logger, e, "Failed to save configuration");
            return false;
        }

    }
    public boolean reportWithEquals(String value){
        return this.reportWith.equals(value);
    }

    /**
     * Getter function for the reportWith global configuration.
     *
     * @return a String containing the reportWith global configuration.
     */
    public String getReportWith() {
        return reportWith;
    }

    /**
     * Setter function for the reportWith global configuration.
     *
     * @param reportWith = A string containing the reportWith global configuration.
     */
    @DataBoundSetter
    public void setReportWith(String reportWith) {
        this.reportWith = reportWith;
    }

    /**
     * Getter function for the targetApiURL global configuration.
     *
     * @return a String containing the targetApiURL global configuration.
     */
    public String getTargetApiURL() {
        return targetApiURL;
    }

    /**
     * Setter function for the targetApiURL global configuration.
     *
     * @param targetApiURL = A string containing the DataDog API URL
     */
    @DataBoundSetter
    public void setTargetApiURL(String targetApiURL) {
        this.targetApiURL = targetApiURL;
    }

    /**
     * Setter function for the targetLogIntakeURL global configuration.
     *
     * @param targetLogIntakeURL = A string containing the DataDog Log Intake URL
     */
    @DataBoundSetter
    public void setTargetLogIntakeURL(String targetLogIntakeURL) {
        this.targetLogIntakeURL = targetLogIntakeURL;
    }

    /**
     * Getter function for the targetLogIntakeURL global configuration.
     *
     * @return a String containing the targetLogIntakeURL global configuration.
     */
    public String getTargetLogIntakeURL() {
        return targetLogIntakeURL;
    }

    /**
     * Getter function for the targetApiKey global configuration.
     *
     * @return a Secret containing the targetApiKey global configuration.
     */
    public Secret getTargetApiKey() {
        return targetApiKey;
    }

    /**
     * Setter function for the apiKey global configuration.
     *
     * @param targetApiKey = A string containing the plaintext representation of a
     *            DataDog API Key
     */
    @DataBoundSetter
    public void setTargetApiKey(final String targetApiKey) {
        this.targetApiKey = Secret.fromString(fixEmptyAndTrim(targetApiKey));
    }

    /**
     * Getter function for the targetHost global configuration.
     *
     * @return a String containing the targetHost global configuration.
     */
    public String getTargetHost() {
        return targetHost;
    }

    /**
     * Setter function for the targetHost global configuration.
     *
     * @param targetHost = A string containing the DogStatsD Host
     */
    @DataBoundSetter
    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    /**
     * Getter function for the targetPort global configuration.
     *
     * @return a Integer containing the targetPort global configuration.
     */
    public Integer getTargetPort() {
        return targetPort;
    }

    /**
     * Setter function for the targetPort global configuration.
     *
     * @param targetPort = A string containing the DogStatsD Port
     */
    @DataBoundSetter
    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }

    /**
     * Getter function for the targetLogCollectionPort global configuration.
     *
     * @return a Integer containing the targetLogCollectionPort global configuration.
     */
    public Integer getTargetLogCollectionPort() {
        return targetLogCollectionPort;
    }

    /**
     * Setter function for the targetLogCollectionPort global configuration.
     *
     * @param targetLogCollectionPort = A string containing the Log Collection Port
     */
    @DataBoundSetter
    public void setTargetLogCollectionPort(Integer targetLogCollectionPort) {
        this.targetLogCollectionPort = targetLogCollectionPort;
    }

    /**
     * Getter function for the targetTraceCollectionPort global configuration.
     *
     * @return a Integer containing the targetTraceCollectionPort global configuration.
     */
    public Integer getTargetTraceCollectionPort() {
        return targetTraceCollectionPort;
    }

    /**
     * Setter function for the targetLogCollectionPort global configuration.
     *
     * @param targetTraceCollectionPort = A string containing the Trace Collection Port
     */
    @DataBoundSetter
    public void setTargetTraceCollectionPort(Integer targetTraceCollectionPort) {
        this.targetTraceCollectionPort = targetTraceCollectionPort;
    }

    /**
     * Getter function for the traceServiceName global configuration.
     *
     * @return a String containing the traceServiceName global configuration.
     * @Deprecated use getCiInstanceName.
     */
    @Deprecated
    public String getTraceServiceName() {
        return traceServiceName;
    }

    /**
     * Setter function for the traceServiceName global configuration.
     *
     * @param traceServiceName = A string containing the Trace Service Name
     * @deprecated Use setCiInstanceName.
     */
    @Deprecated
    @DataBoundSetter
    public void setTraceServiceName(String traceServiceName) {
        this.traceServiceName = traceServiceName;
    }

    /**
     * Getter function for the traceServiceName global configuration.
     *
     * @return a String containing the traceServiceName global configuration.
     */
    public String getCiInstanceName() {
        return this.traceServiceName;
    }

    /**
     * Setter function for the traceServiceName global configuration.
     *
     * @param ciInstanceName = A string containing the CI Instance Name
     */
    public void setCiInstanceName(String ciInstanceName) {
        this.traceServiceName = ciInstanceName;
    }

    /**
     * Getter function for the hostname global configuration.
     *
     * @return a String containing the hostname global configuration.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Setter function for the hostname global configuration.
     *
     * @param hostname - A String containing the hostname of the Jenkins host.
     */
    @DataBoundSetter
    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    /**
     * @deprecated replaced by {@link #getExcluded()}
     * @return a String array containing the excluded global configuration.
    **/
    @Deprecated
    public String getBlacklist() {
        return blacklist;
    }
    
    /**
     * Getter function for the excluded global configuration, containing
     * a comma-separated list of jobs to exclude from monitoring.
     *
     * @return a String array containing the excluded global configuration.
     */
    public String getExcluded() {
        return blacklist;
    }

    /**
     * @deprecated replaced by {@link #setExcluded(String)}
     * @param jobs - a comma-separated list of jobs to exclude from monitoring.
    **/
    @Deprecated
    @DataBoundSetter
    public void setBlacklist(final String jobs) {
        this.blacklist = jobs;
    }
    
    /**
     * Setter function for the excluded jobs global configuration,
     * accepting a comma-separated string of jobs.
     *
     * @param jobs - a comma-separated list of jobs to exclude from monitoring.
     */
    @DataBoundSetter
    public void setExcluded(final String jobs) {
        this.blacklist = jobs;
    }

    /**
     * @deprecated replaced by {@link #getIncluded()}
     * @return a String array containing the included global configuration.
    **/
    @Deprecated
    public String getWhitelist() {
        return whitelist;
    }
    
    /**
     * Getter function for the included global configuration, containing
     * a comma-separated list of jobs to include for monitoring.
     *
     * @return a String array containing the included global configuration.
     */
    public String getIncluded() {
        return whitelist;
    }

    /**
     * @deprecated replaced by {@link #setIncluded(String)}
     * @param jobs - a comma-separated list of jobs to include for monitoring.
    **/
    @Deprecated
    @DataBoundSetter
    public void setWhitelist(final String jobs) {
        this.whitelist = jobs;
    }
    
    /**
     * Setter function for the includedd global configuration,
     * accepting a comma-separated string of jobs.
     *
     * @param jobs - a comma-separated list of jobs to include for monitoring.
     */
    @DataBoundSetter
    public void setIncluded(final String jobs) {
        this.whitelist = jobs;
    }

    /**
     * Gets the globalTagFile set in the job configuration.
     *
     * @return a String representing the relative path to a globalTagFile
     */
    public String getGlobalTagFile() {
        return globalTagFile;
    }

    /**
     * Setter function for the globalFile global configuration,
     * accepting a comma-separated string of tags.
     *
     * @param globalTagFile - a comma-separated list of tags.
     */
    @DataBoundSetter
    public void setGlobalTagFile(String globalTagFile) {
        this.globalTagFile = globalTagFile;
    }

    /**
     * Getter function for the globalTags global configuration, containing
     * a comma-separated list of tags that should be applied everywhere.
     *
     * @return a String array containing the globalTags global configuration
     */
    public String getGlobalTags() {
        return globalTags;
    }

    /**
     * Setter function for the globalTags global configuration,
     * accepting a comma-separated string of tags.
     *
     * @param globalTags - a comma-separated list of tags.
     */
    @DataBoundSetter
    public void setGlobalTags(String globalTags) {
        this.globalTags = globalTags;
    }

    /**
     * Getter function for the globalJobTags global configuration, containing
     * a comma-separated list of jobs and tags that should be applied to them
     *
     * @return a String array containing the globalJobTags global configuration.
     */
    public String getGlobalJobTags() {
        return globalJobTags;
    }

    /**
     * Setter function for the globalJobTags global configuration,
     * accepting a comma-separated string of jobs and tags.
     *
     * @param globalJobTags - a comma-separated list of jobs to include from monitoring.
     */
    @DataBoundSetter
    public void setGlobalJobTags(String globalJobTags) {
        this.globalJobTags = globalJobTags;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to emit Security related events.
     */
    public boolean isEmitSecurityEvents() {
        return emitSecurityEvents;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param emitSecurityEvents - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    public void setEmitSecurityEvents(boolean emitSecurityEvents) {
        this.emitSecurityEvents = emitSecurityEvents;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to emit System related events.
     */
    public boolean isEmitSystemEvents() {
        return emitSystemEvents;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param emitSystemEvents - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    public void setEmitSystemEvents(boolean emitSystemEvents) {
        this.emitSystemEvents = emitSystemEvents;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to emit Config Change events.
     */
    public boolean isEmitConfigChangeEvents() {
        return emitConfigChangeEvents;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param emitConfigChangeEvents - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    public void setEmitConfigChangeEvents(boolean emitConfigChangeEvents) {
        this.emitConfigChangeEvents = emitConfigChangeEvents;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to collect logs.
     */
    public boolean isCollectBuildLogs() {
        return collectBuildLogs;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param collectBuildLogs - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    public void setCollectBuildLogs(boolean collectBuildLogs) {
        this.collectBuildLogs = collectBuildLogs;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to collect traces.
     * @deprecated Use isEnabledCiVisibility
     */
    @Deprecated
    public boolean isCollectBuildTraces() {
        return collectBuildTraces;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param collectBuildTraces - The checkbox status (checked/unchecked)
     * @deprecated Use setEnableCiVisibility
     */
    @DataBoundSetter
    @Deprecated
    public void setCollectBuildTraces(boolean collectBuildTraces) {
        this.collectBuildTraces = collectBuildTraces;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to enable CI Visibility.
     */
    public boolean isEnabledCiVisibility() {
        return this.collectBuildTraces;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding to enable CI Visibility
     *
     * @param enableCiVisibility - The checkbox status (checked/unchecked)
     * @deprecated Use setEnableCiVisibility
     */
    @DataBoundSetter
    public void setEnableCiVisibility(boolean enableCiVisibility) {
        this.collectBuildTraces = enableCiVisibility;
    }
}
