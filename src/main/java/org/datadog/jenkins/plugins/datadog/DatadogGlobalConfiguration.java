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
import hudson.util.FormValidation.Kind;
import hudson.util.ListBoxModel;
import hudson.model.Item;
import hudson.security.ACL;

import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.clients.DatadogHttpClient;
import org.datadog.jenkins.plugins.datadog.clients.DatadogAgentClient;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.config.DatadogAgentConfiguration;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.AncestorInPath;

import javax.management.InvalidAttributeValueException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Extension
public class DatadogGlobalConfiguration extends GlobalConfiguration {

    private static final Logger logger = Logger.getLogger(DatadogGlobalConfiguration.class.getName());
    private static final String DISPLAY_NAME = "Datadog Plugin";

    // Event String constants
    public static final String SYSTEM_EVENTS = "ItemLocationChanged,"
            + "ComputerOnline,ComputerOffline,ComputerTemporarilyOnline,ComputerTemporarilyOffline,"
            + "ComputerLaunchFailure,ItemCreated,ItemDeleted,ItemUpdated,ItemCopied";
    public static final String SECURITY_EVENTS = "UserAuthenticated,UserFailedToAuthenticate,UserLoggedOut";
    public static final String CONFIG_CHANGED_EVENT = "ConfigChanged"; 
    public static final String DEFAULT_EVENTS = "BuildStarted,BuildAborted,BuildCompleted,SCMCheckout";

    // Standard Agent EnvVars
    public static final String DD_AGENT_HOST = "DD_AGENT_HOST";
    public static final String DD_AGENT_PORT = "DD_AGENT_PORT";
    public static final String DD_TRACE_AGENT_PORT = "DD_TRACE_AGENT_PORT";
    public static final String DD_TRACE_AGENT_URL = "DD_TRACE_AGENT_URL";

    // Env Var key to get the hostname from the Jenkins workers.
    public static final String DD_CI_HOSTNAME = "DD_CI_HOSTNAME";

    // Jenkins Agent EnvVars
    public static final String TARGET_HOST_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_HOST";
    public static final String TARGET_PORT_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_PORT";
    public static final String TARGET_TRACE_COLLECTION_PORT_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_TRACE_COLLECTION_PORT";

    private static final String REPORT_WITH_PROPERTY = "DATADOG_JENKINS_PLUGIN_REPORT_WITH";
    private static final String TARGET_API_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_URL";
    private static final String TARGET_LOG_INTAKE_URL_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_LOG_INTAKE_URL";
    private static final String TARGET_WEBHOOK_INTAKE_URL_PROPERTY = "DATADOG_JENKINS_TARGET_WEBHOOK_INTAKE_URL";
    private static final String TARGET_API_KEY_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_API_KEY";
    private static final String TARGET_LOG_COLLECTION_PORT_PROPERTY = "DATADOG_JENKINS_PLUGIN_TARGET_LOG_COLLECTION_PORT";
    private static final String TARGET_TRACE_SERVICE_NAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_TRACE_SERVICE_NAME";
    private static final String HOSTNAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_HOSTNAME";
    private static final String EXCLUDED_PROPERTY = "DATADOG_JENKINS_PLUGIN_EXCLUDED";
    private static final String INCLUDED_PROPERTY = "DATADOG_JENKINS_PLUGIN_INCLUDED";
    //Deprecated
    private static final String BLACKLIST_PROPERTY = "DATADOG_JENKINS_PLUGIN_BLACKLIST";
    private static final String WHITELIST_PROPERTY = "DATADOG_JENKINS_PLUGIN_WHITELIST";

    private static final String GLOBAL_TAG_FILE_PROPERTY = "DATADOG_JENKINS_PLUGIN_GLOBAL_TAG_FILE";
    private static final String GLOBAL_TAGS_PROPERTY = "DATADOG_JENKINS_PLUGIN_GLOBAL_TAGS";
    private static final String GLOBAL_JOB_TAGS_PROPERTY = "DATADOG_JENKINS_PLUGIN_GLOBAL_JOB_TAGS";
    private static final String EMIT_SECURITY_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_EMIT_SECURITY_EVENTS";
    private static final String EMIT_SYSTEM_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_EMIT_SYSTEM_EVENTS";
    private static final String EMIT_CONFIG_CHANGE_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_EMIT_CONFIG_CHANGE_EVENTS";
    private static final String INCLUDE_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_INCLUDE_EVENTS";
    private static final String EXCLUDE_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_EXCLUDE_EVENTS";
    private static final String COLLECT_BUILD_LOGS_PROPERTY = "DATADOG_JENKINS_PLUGIN_COLLECT_BUILD_LOGS";
    private static final String RETRY_LOGS_PROPERTY = "DATADOG_JENKINS_PLUGIN_RETRY_LOGS";
    private static final String REFRESH_DOGSTATSD_CLIENT_PROPERTY = "DATADOG_REFRESH_STATSD_CLIENT";
    private static final String CACHE_BUILD_RUNS_PROPERTY = "DATADOG_CACHE_BUILD_RUNS";
    private static final String USE_AWS_INSTANCE_HOSTNAME_PROPERTY = "DATADOG_USE_AWS_INSTANCE_HOSTNAME";

    private static final String ENABLE_CI_VISIBILITY_PROPERTY = "DATADOG_JENKINS_PLUGIN_ENABLE_CI_VISIBILITY";
    private static final String CI_VISIBILITY_CI_INSTANCE_NAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_CI_VISIBILITY_CI_INSTANCE_NAME";

    private static final String DEFAULT_REPORT_WITH_VALUE = DatadogClient.ClientType.HTTP.name();
    private static final String DEFAULT_TARGET_API_URL_VALUE = "https://api.datadoghq.com/api/";
    private static final String DEFAULT_TARGET_LOG_INTAKE_URL_VALUE = "https://http-intake.logs.datadoghq.com/v1/input/";
    private static final String DEFAULT_TARGET_WEBHOOK_INTAKE_URL_VALUE = "https://webhook-intake.datadoghq.com/api/v2/webhook/";
    private static final String DEFAULT_TARGET_HOST_VALUE = "localhost";
    private static final Integer DEFAULT_TARGET_PORT_VALUE = 8125;
    private static final Integer DEFAULT_TRACE_COLLECTION_PORT_VALUE = 8126;
    private static final String DEFAULT_CI_INSTANCE_NAME = "jenkins";

    private static final Integer DEFAULT_TARGET_LOG_COLLECTION_PORT_VALUE = null;
    private static final boolean DEFAULT_EMIT_SECURITY_EVENTS_VALUE = true;
    private static final boolean DEFAULT_EMIT_SYSTEM_EVENTS_VALUE = true;
    private static final boolean DEFAULT_EMIT_CONFIG_CHANGE_EVENTS_VALUE = false;
    private static final boolean DEFAULT_COLLECT_BUILD_LOGS_VALUE = false;
    private static final boolean DEFAULT_COLLECT_BUILD_TRACES_VALUE = false;
    private static final boolean DEFAULT_RETRY_LOGS_VALUE = true;
    private static final boolean DEFAULT_REFRESH_DOGSTATSD_CLIENT_VALUE = false;
    private static final boolean DEFAULT_CACHE_BUILD_RUNS_VALUE = true;
    private static final boolean DEFAULT_USE_AWS_INSTANCE_HOSTNAME_VALUE = false;

    private String reportWith = DEFAULT_REPORT_WITH_VALUE;
    private String targetApiURL = DEFAULT_TARGET_API_URL_VALUE;
    private String targetLogIntakeURL = DEFAULT_TARGET_LOG_INTAKE_URL_VALUE;
    private String targetWebhookIntakeURL = DEFAULT_TARGET_WEBHOOK_INTAKE_URL_VALUE;
    private Secret targetApiKey = null;
    private String targetCredentialsApiKey = null;
    private Secret usedApiKey = null;
    private String targetHost = DEFAULT_TARGET_HOST_VALUE;
    private Integer targetPort = DEFAULT_TARGET_PORT_VALUE;
    private Integer targetLogCollectionPort = DEFAULT_TARGET_LOG_COLLECTION_PORT_VALUE;
    private Integer targetTraceCollectionPort = DEFAULT_TRACE_COLLECTION_PORT_VALUE;
    private String traceServiceName = DEFAULT_CI_INSTANCE_NAME;
    private String hostname = null;
    private String blacklist = null;
    private String whitelist = null;
    private String globalTagFile = null;
    private String globalTags = null;
    private String globalJobTags = null;
    private String includeEvents = null;
    private String excludeEvents = null;
    private boolean emitSecurityEvents = DEFAULT_EMIT_SECURITY_EVENTS_VALUE;
    private boolean emitSystemEvents = DEFAULT_EMIT_SYSTEM_EVENTS_VALUE;
    private boolean emitConfigChangeEvents = DEFAULT_EMIT_CONFIG_CHANGE_EVENTS_VALUE;
    private boolean collectBuildLogs = DEFAULT_COLLECT_BUILD_LOGS_VALUE;
    private boolean collectBuildTraces = DEFAULT_COLLECT_BUILD_TRACES_VALUE;
    private boolean retryLogs = DEFAULT_RETRY_LOGS_VALUE;
    private boolean refreshDogstatsdClient = DEFAULT_REFRESH_DOGSTATSD_CLIENT_VALUE;
    private boolean cacheBuildRuns = DEFAULT_CACHE_BUILD_RUNS_VALUE;
    private boolean useAwsInstanceHostname = DEFAULT_USE_AWS_INSTANCE_HOSTNAME_VALUE;

    private List<String> includedEvents = new ArrayList<String>();
    private boolean includeEventsIsEnv = false;
    private boolean excludeEventsIsEnv = false;

    @DataBoundConstructor
    public DatadogGlobalConfiguration() {
        load(); // Load the persisted global configuration
        loadEnvVariables(); // Load environment variables after as they should take precedence.
    }

    public void loadEnvVariables() {
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
        if(StringUtils.isNotBlank(targetLogIntakeURLEnvVar)){
            this.targetLogIntakeURL = targetLogIntakeURLEnvVar;
        }

        String targetWebhookIntakeURLEnvVar = System.getenv(TARGET_WEBHOOK_INTAKE_URL_PROPERTY);
        if(StringUtils.isNotBlank(targetWebhookIntakeURLEnvVar)){
            this.targetWebhookIntakeURL = targetWebhookIntakeURLEnvVar;
        }

        String targetApiKeyEnvVar = System.getenv(TARGET_API_KEY_PROPERTY);
        if(StringUtils.isNotBlank(targetApiKeyEnvVar)){
            this.targetApiKey = Secret.fromString(targetApiKeyEnvVar);
        }

        final DatadogAgentConfiguration agentConfig = DatadogAgentConfiguration.resolve(System.getenv());
        if(StringUtils.isNotBlank(agentConfig.getHost())){
            this.targetHost = agentConfig.getHost();
        }

        if(agentConfig.getPort() != null){
            this.targetPort = agentConfig.getPort();
        }

        if(agentConfig.getTracesPort() != null) {
            this.targetTraceCollectionPort = agentConfig.getTracesPort();
        }

        String targetLogCollectionPortEnvVar = System.getenv(TARGET_LOG_COLLECTION_PORT_PROPERTY);
        if(StringUtils.isNotBlank(targetLogCollectionPortEnvVar) && StringUtils.isNumeric(targetLogCollectionPortEnvVar)){
            this.targetLogCollectionPort = Integer.valueOf(targetLogCollectionPortEnvVar);
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

        String includeEventsEnvVar = System.getenv(INCLUDE_EVENTS_PROPERTY);
        if(StringUtils.isNotBlank(includeEventsEnvVar)){
            this.includeEvents = includeEventsEnvVar;
            this.includeEventsIsEnv = true;
        }

        String excludeEventsEnvVar = System.getenv(EXCLUDE_EVENTS_PROPERTY);
        if(StringUtils.isNotBlank(excludeEventsEnvVar)){
            this.excludeEvents = excludeEventsEnvVar;
            this.excludeEventsIsEnv = true;
        }

        String collectBuildLogsEnvVar = System.getenv(COLLECT_BUILD_LOGS_PROPERTY);
        if(StringUtils.isNotBlank(collectBuildLogsEnvVar)){
            this.collectBuildLogs = Boolean.valueOf(collectBuildLogsEnvVar);
        }

        String retryLogsEnvVar = System.getenv(RETRY_LOGS_PROPERTY);
        if(StringUtils.isNotBlank(retryLogsEnvVar)){
            this.retryLogs = Boolean.valueOf(retryLogsEnvVar);
        }

        String refreshDogstatsdClientEnvVar = System.getenv(REFRESH_DOGSTATSD_CLIENT_PROPERTY);
        if(StringUtils.isNotBlank(refreshDogstatsdClientEnvVar)){
            this.refreshDogstatsdClient = Boolean.valueOf(refreshDogstatsdClientEnvVar);
        }

        String cacheBuildRunsEnvVar = System.getenv(CACHE_BUILD_RUNS_PROPERTY);
        if(StringUtils.isNotBlank(cacheBuildRunsEnvVar)){
            this.cacheBuildRuns = Boolean.valueOf(cacheBuildRunsEnvVar);
        }

        String useAwsInstanceHostnameEnvVar = System.getenv(USE_AWS_INSTANCE_HOSTNAME_PROPERTY);
        if(StringUtils.isNotBlank(useAwsInstanceHostnameEnvVar)){
            this.useAwsInstanceHostname = Boolean.valueOf(useAwsInstanceHostnameEnvVar);
        }

        String enableCiVisibilityVar = System.getenv(ENABLE_CI_VISIBILITY_PROPERTY);
        if(StringUtils.isNotBlank(enableCiVisibilityVar)) {
            this.collectBuildTraces = Boolean.valueOf(enableCiVisibilityVar);
        }

        String ciVisibilityCiInstanceNameVar = System.getenv(CI_VISIBILITY_CI_INSTANCE_NAME_PROPERTY);
        if(StringUtils.isNotBlank(ciVisibilityCiInstanceNameVar)) {
            this.traceServiceName = ciVisibilityCiInstanceNameVar;
        }

        this.createIncludeLists();
    }

    /**
     * Test the connection to the Logs Collection port in the Datadog Agent.
     *
     * @param targetHost - The Datadog Agent host
     * @param targetLogCollectionPort - The Logs Collection port used to report logs in the Datadog Agent
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    public FormValidation doCheckAgentConnectivityLogs(@QueryParameter("targetHost") String targetHost, @QueryParameter("targetLogCollectionPort") String targetLogCollectionPort) {
        return checkAgentConnectivity(targetHost, targetLogCollectionPort);
    }

    /**
     * Test the connection to the Traces Collection port in the Datadog Agent.
     *
     * @param targetHost - The Datadog Agent host
     * @param targetTraceCollectionPort - The Traces Collection port used to report logs in the Datadog Agent
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */

    public FormValidation doCheckAgentConnectivityTraces(@QueryParameter("targetHost") String targetHost, @QueryParameter("targetTraceCollectionPort") String targetTraceCollectionPort) {
        return checkAgentConnectivity(targetHost, targetTraceCollectionPort);
    }

    private FormValidation checkAgentConnectivity(final String host, final String port) {
        if(host == null || host.isEmpty()) {
            return FormValidation.error("The Agent host cannot be empty.");
        }

        if(port != null && !port.isEmpty()) {
            if(!validatePort(port)) {
                return FormValidation.error("The port is not valid");
            }

            final DatadogAgentClient.ConnectivityResult connectivity = DatadogAgentClient.checkConnectivity(host, Integer.parseInt(port));
            if(connectivity.isError()) {
                return FormValidation.error("Connection to " + host + ":" + port + " FAILED: " + connectivity.getErrorMessage());
            }
        } else {
            return FormValidation.error("The port cannot be empty.");
        }

        return FormValidation.ok("Success!");
    }

     /**
     * Gets the StringCredentials object for the given credential ID
     *
     * @param credentialId - The Id of the credential to get
     * @return a StringCredentials object
     */
    public StringCredentials getCredentialFromId(String credentialId) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                    StringCredentials.class,
                    Jenkins.get(),
                    ACL.SYSTEM,
                    URIRequirementBuilder.fromUri(null).build()),
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialId))
        );
    }

    /**
     * Gets the correct Secret object representing the API key used for authentication to Datadog
     * If a Credential is provided, then use the credential, if not, default to the text submission
     *
     * @param apiKey - The text API key the user submitted
     * @param credentialsApiKey - The Id of the credential the user submitted
     * @return a Secret object representing the API key used for authentication to Datadog
     */
    public Secret findSecret(String apiKey, String credentialsApiKey) {
        Secret secret = Secret.fromString(apiKey);
        if (credentialsApiKey != null && !StringUtils.isBlank(credentialsApiKey)) {
            StringCredentials credential = this.getCredentialFromId(credentialsApiKey);
            if (credential != null && !credential.getSecret().getPlainText().isEmpty()){
                secret = credential.getSecret();
            }
        }
        return secret;
    }

     /**
     * Tests the apiKey field from the configuration screen, to check its' validity.
     * It is used in the config.jelly resource file. See method="testConnection"
     *
     * @param targetApiURL - The API Url to validate the apikey.
     * @param targetApiKey - A String containing the apiKey submitted from the form on the
     *                   configuration screen, which will be used to authenticate a request to the
     *                   Datadog API.
     * @param targetCredentialsApiKey - A String containing the API key as a credential, if it is not specified,
                         try the connection with the targetApiKey
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     * @throws IOException      if there is an input/output exception.
     * @throws ServletException if there is a servlet exception.
     */
    @RequirePOST
    public FormValidation doTestConnection(
            @QueryParameter("targetApiKey") final String targetApiKey,
            @QueryParameter("targetCredentialsApiKey") final String targetCredentialsApiKey, 
            @QueryParameter("targetApiURL") final String targetApiURL)
            throws IOException, ServletException {

        final Secret secret = findSecret(targetApiKey, targetCredentialsApiKey);
        if (DatadogHttpClient.validateDefaultIntakeConnection(targetApiURL, secret)) {
            return FormValidation.ok("Great! Your API key is valid.");
        } else {
            return FormValidation.error("Hmmm, your API key seems to be invalid.");
        }
    }

    /**
     * Populates the targetCredentialsApiKey field from the configuration screen with all of the valid credentials
     *
     * @param item - The context within which to list available credentials
     * @param targetCredentialsApiKey - A String containing the API key as a credential
     * @return a ListBoxModel object used to display all of the available credentials.
     */
    public ListBoxModel doFillTargetCredentialsApiKeyItems(
        @AncestorInPath Item item,
        @QueryParameter("targetCredentialsApiKey") String targetCredentialsApiKey
        ) {
        StandardListBoxModel result = new StandardListBoxModel();
        // If the users does not have permissions to list credentials, only list the current value
        if (item == null) {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return result.includeCurrentValue(targetCredentialsApiKey);
            }
        } else {
            if (!item.hasPermission(Item.EXTENDED_READ)
                && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return result.includeCurrentValue(targetCredentialsApiKey);
            }
        }
        return result.includeEmptyValue()
            .includeMatchingAs(ACL.SYSTEM,
                               Jenkins.get(),
                               StringCredentials.class,
                               Collections.emptyList(),
                               CredentialsMatchers.instanceOf(StringCredentials.class))
            .includeCurrentValue(targetCredentialsApiKey);
    }

    /**
     * Checks filtering config for comma-separated list, overlapping include/exclude lists,
     * unrecognizable event names, and redundant inclusion/exclusion. 
     * 
     * @param emitSecurityEvents toggle to send security events
     * @param emitSystemEvents toggle to send system events
     * @param includeEvents string of included events list (comma-separated)
     * @param excludeEvents string of excluded events list (comma-separated)
     * @return FormValidation.error() if not formatted correctly or overlapping lists,
     * FormValidation.warning() for redundant config, and FormValidation.ok() for all else
     */
    public FormValidation doTestFilteringConfig(
        @QueryParameter("emitSecurityEvents") boolean emitSecurityEvents,
        @QueryParameter("emitSystemEvents") boolean emitSystemEvents,
        @QueryParameter("includeEvents") String includeEvents,
        @QueryParameter("excludeEvents") String excludeEvents
    ) {
        return checkConfig(emitSecurityEvents, emitSystemEvents, includeEvents,
                excludeEvents);
    }

    /**
     * Tests the targetCredentialsApiKey field from the configuration screen, to check its' validity.
     *
     * @param item - The context within which to list available credentials.
     * @param targetCredentialsApiKey - A String containing the API key as a credential
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    @RequirePOST
    public FormValidation doCheckTargetCredentialsApiKey(
        @AncestorInPath Item item,
        @QueryParameter("targetCredentialsApiKey") String targetCredentialsApiKey
    ) {
        // Don't validate for users that do not have permission to list credentials
        if (item == null) {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return FormValidation.ok();
            }
        } else {
            if (!item.hasPermission(Item.EXTENDED_READ)
                && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return FormValidation.ok(); 
            }
        }
        if (StringUtils.isBlank(targetCredentialsApiKey)) {
            return FormValidation.ok();
        }
        if (targetCredentialsApiKey.startsWith("${") && targetCredentialsApiKey.endsWith("}")) {
            return FormValidation.warning("Cannot validate expression based credentials");
        }
        if (CredentialsProvider.listCredentials(StringCredentials.class,
                        item,
                        ACL.SYSTEM, 
                        Collections.emptyList(),
                        CredentialsMatchers.withId(targetCredentialsApiKey)).isEmpty()) {
            return FormValidation.error("Cannot find currently selected credentials");
        }
        return FormValidation.ok();
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

    /**
     * @param targetWebhookIntakeURL - The Webhook Intake URL which the plugin will report to.
     * @return a FormValidation object used to display a message to the user on the configuration
     * screen.
     */
    @RequirePOST
    public FormValidation doCheckTargetWebhookIntakeURL(@QueryParameter("targetWebhookIntakeURL") final String targetWebhookIntakeURL) {
        if (!validateURL(targetWebhookIntakeURL) && collectBuildTraces) {
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
        return StringUtils.isNotBlank(targetPort) && StringUtils.isNumeric(targetPort) && NumberUtils.createInteger(targetPort) >= 0;
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
            this.setTargetWebhookIntakeURL(formData.getString("targetWebhookIntakeURL"));
            this.setTargetApiKey(formData.getString("targetApiKey"));
            this.setTargetCredentialsApiKey(formData.getString("targetCredentialsApiKey"));
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
                this.setTargetTraceCollectionPort(null);
            }

            try {
                final JSONObject ciVisibilityData = formData.getJSONObject("ciVisibilityData");
                if (ciVisibilityData != null && !ciVisibilityData.isNullObject()) {
                    if (!"DSD".equalsIgnoreCase(reportWith)) {
                        if (!validateURL(formData.getString("targetWebhookIntakeURL"))) {
                            throw new FormException("CI Visibility requires a Webhook Intake URL", "targetWebhookIntakeURL");
                        }
                    } else {
                        if(!validatePort(traceCollectionPortStr)) {
                            throw new FormException("CI Visibility requires a valid Trace Collection port", "collectBuildTraces");
                        }
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
            this.setRetryLogs(formData.getBoolean("retryLogs"));
            this.setRefreshDogstatsdClient(formData.getBoolean("refreshDogstatsdClient"));
            this.setCacheBuildRuns(formData.getBoolean("cacheBuildRuns"));
            this.setUseAwsInstanceHostname(formData.getBoolean("useAwsInstanceHostname"));

            boolean emitSecurityEvents = formData.getBoolean("emitSecurityEvents");
            boolean emitSystemEvents = formData.getBoolean("emitSystemEvents");
            String includeEvents = formData.getString("includeEvents");
            String excludeEvents = formData.getString("excludeEvents");
            FormValidation configStatus = this.checkConfig(emitSecurityEvents, emitSystemEvents, includeEvents, excludeEvents);

            if (configStatus.kind == Kind.ERROR) {
                String message = configStatus.getMessage();
                String formField = !message.contains("included") ? "excludeEvents" : "includeEvents";
                throw new FormException(message, formField);
            }

            this.setEmitSecurityEvents(emitSecurityEvents);
            this.setEmitSystemEvents(emitSystemEvents);
            this.setIncludeEvents(includeEvents);
            this.setExcludeEvents(excludeEvents);

            boolean collectBuildLogs = formData.getBoolean("collectBuildLogs");
            if ("DSD".equalsIgnoreCase(reportWith) && collectBuildLogs && !validatePort(logCollectionPortStr)) {
                throw new FormException("Logs Collection requires a valid Log Collection port", "collectBuildLogs");
            }
            this.setCollectBuildLogs(formData.getBoolean("collectBuildLogs"));

            final Secret apiKeySecret = findSecret(formData.getString("targetApiKey"), formData.getString("targetCredentialsApiKey"));
            this.setUsedApiKey(apiKeySecret);
            //When form is saved....
            DatadogClient client = ClientFactory.getClient(DatadogClient.ClientType.valueOf(this.getReportWith()), this.getTargetApiURL(),
                this.getTargetLogIntakeURL(), this.getTargetWebhookIntakeURL(), this.getUsedApiKey(), this.getTargetHost(),
                this.getTargetPort(), this.getTargetLogCollectionPort(), this.getTargetTraceCollectionPort(), this.getCiInstanceName());
                // ...reinitialize the DatadogClient
            if(client == null) {
                return false;
            }
            client.setDefaultIntakeConnectionBroken(false);
            client.setLogIntakeConnectionBroken(false);
            client.setWebhookIntakeConnectionBroken(false);
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
     * Setter function for the targetWebhookIntakeURL global configuration.
     *
     * @param targetWebhookIntakeURL = A string containing the DataDog Webhook Intake URL
     */
    @DataBoundSetter
    public void setTargetWebhookIntakeURL(String targetWebhookIntakeURL) {
        this.targetWebhookIntakeURL = targetWebhookIntakeURL;
    }

    /**
     * Getter function for the targetWebhookIntakeURL global configuration.
     *
     * @return a String containing the targetWebhookIntakeURL global configuration.
     */
    public String getTargetWebhookIntakeURL() {
        return targetWebhookIntakeURL;
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
     * Getter function for the API key global configuration.
     *
     * @return a Secret containing the usedApiKey global configuration.
     */
    public Secret getUsedApiKey() {
        return usedApiKey;
    }

    /**
     * Setter function for the API key global configuration..
     *
     * @param usedApiKey = A Secret containing the DataDog API Key
     */
    @DataBoundSetter
    public void setUsedApiKey(final Secret usedApiKey) {
        this.usedApiKey = usedApiKey;
    }

    /**
     * Getter function for the targetCredentialsApiKey global configuration.
     *
     * @return a String containing the ID of the targetCredentialsApiKey global configuration.
     */
    public String getTargetCredentialsApiKey() {
        return targetCredentialsApiKey;
    }

    /**
     * Setter function for the credentials apiKey global configuration.
     *
     * @param targetCredentialsApiKey = A string containing the plaintext representation of a
     *            DataDog API Key
     */
    @DataBoundSetter
    public void setTargetCredentialsApiKey(final String targetCredentialsApiKey) {
        this.targetCredentialsApiKey = targetCredentialsApiKey;
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
     * @deprecated use getCiInstanceName.
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
     * @return - A {@link Boolean} indicating if the user has configured Datadog to retry sending logs.
     */
    public boolean isRetryLogs() {
        return retryLogs;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param retryLogs - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    public void setRetryLogs(boolean retryLogs) {
        this.retryLogs = retryLogs;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to refresh the dogstatsd client
     */
    public boolean isRefreshDogstatsdClient() {
        return refreshDogstatsdClient;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param refreshDogstatsdClient - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    public void setRefreshDogstatsdClient(boolean refreshDogstatsdClient) {
        this.refreshDogstatsdClient = refreshDogstatsdClient;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to cache build runs
     */
    public boolean isCacheBuildRuns() {
        return cacheBuildRuns;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param cacheBuildRuns - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    public void setCacheBuildRuns(boolean cacheBuildRuns) {
        this.cacheBuildRuns = cacheBuildRuns;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to use AWS instance as hostname
     */
    public boolean isUseAwsInstanceHostname() {
        return useAwsInstanceHostname;
    }


    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param useAwsInstanceHostname - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    public void setUseAwsInstanceHostname(boolean useAwsInstanceHostname) {
        this.useAwsInstanceHostname = useAwsInstanceHostname;
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
        this.createIncludeLists();
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
        this.createIncludeLists();
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to emit Config Change events.
     */
    @Deprecated
    public boolean isEmitConfigChangeEvents() {
        return emitConfigChangeEvents;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding
     *
     * @param emitConfigChangeEvents - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    @Deprecated
    public void setEmitConfigChangeEvents(boolean emitConfigChangeEvents) {
        this.emitConfigChangeEvents = emitConfigChangeEvents;
        this.createIncludeLists();
    }

    /**
     * Setter function for the included global configuration,
     * accepting a comma-separated string of events.
     *
     * @param events - a comma-separated list of events to include for sending to agent.
     */
    @DataBoundSetter
    public void setIncludeEvents(String events) throws InvalidAttributeValueException {
        if (this.isOverlappingStrings(events, this.excludeEvents)) {
            throw new InvalidAttributeValueException("Included events and excluded events contain an overlap.");
        }  
        
        this.includeEvents = events;
        this.includeEventsIsEnv = false;
        this.createIncludeLists();
    }

    /**
     * Getter function for the included global configuration, containing
     * a comma-separated list of events to send to agent.
     *
     * @return a String array containing the events included global configuration.
     */
    public String getIncludeEvents() {
        return includeEvents;
    }

    /**
     * Setter function for the included global configuration,
     * accepting a comma-separated string of events.
     *
     * @param events - a comma-separated list of events to exclude for sending to agent.
     */
    @DataBoundSetter
    public void setExcludeEvents(String events) throws InvalidAttributeValueException{
        if (this.isOverlappingStrings(events, this.includeEvents)) {
            throw new InvalidAttributeValueException("Included events and excluded events contain an overlap.");
        }
        
        this.excludeEvents = events;
        this.excludeEventsIsEnv = false;
        this.createIncludeLists();
    }

    /**
     * Getter function for the included global configuration, containing
     * a comma-separated list of events not to send to agent.
     *
     * @return a String array containing the events included global configuration.
     */
    public String getExcludeEvents() {
        return excludeEvents;
    }

    /**
     * Getter function for the included global configuration, containing
     * a list of events to send to agent.
     *
     * @return a String ArrayList containing the events included global configuration.
     */
    public List<String> getListOfIncludedEvents() {
        return includedEvents;
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
    public boolean getEnableCiVisibility() {
        return this.collectBuildTraces;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding to enable CI Visibility
     *
     * @param enableCiVisibility - The checkbox status (checked/unchecked)
     */
    @DataBoundSetter
    public void setEnableCiVisibility(boolean enableCiVisibility) {
        this.collectBuildTraces = enableCiVisibility;
    }

    /**
     * Helper function to determine if strings are overlapping in any way.
     * 
     * @param firstString first string
     * @param stringToCompare second string
     * @return true if strings are overlapping (shared event)
     */
    private boolean isOverlappingStrings(String firstString, String stringToCompare) {
        if (stringToCompare == null || stringToCompare.isEmpty()) return false;

        return Arrays.asList(stringToCompare.split(",")).stream().anyMatch(firstString::contains);
    }

    /**
     * Creates inclusion list for events by looking at toggles and inclusion/exclusion string lists
     */
    private void createIncludeLists() {
        this.includedEvents = new ArrayList<String>(Arrays.asList(DEFAULT_EVENTS.split(",")));

        // applies exclusion first for default events
        if (this.excludeEvents != null) this.includedEvents.removeIf(this.excludeEvents::contains);

        if (this.includeEvents != null && !this.includeEvents.isEmpty()) {
            this.includedEvents.addAll(Arrays.asList(this.includeEvents.split(",")));
        }

        if (this.emitSystemEvents) {
            this.includedEvents.addAll(new ArrayList<String>(Arrays.asList(SYSTEM_EVENTS.split(","))));
        }

        if (this.emitSecurityEvents) {
            this.includedEvents.addAll(new ArrayList<String>(Arrays.asList(SECURITY_EVENTS.split(","))));
        }

        this.includedEvents = this.includedEvents.stream().distinct().collect(Collectors.toList());

        if (this.excludeEvents == null || this.excludeEvents.isEmpty()) return;
        
        // no exclusion rules for included events set in env var
        if (this.includeEventsIsEnv && !this.excludeEventsIsEnv) return;

        this.includedEvents.removeIf(this.excludeEvents::contains);
    }

    /**
     * @see #doTestFilteringConfig
     */
    private FormValidation checkConfig(boolean emitSecurityEvents, boolean emitSystemEvents,
            String includeEvents, String excludeEvents) {
        String commaSeparatedRegex = "((\\w+,)*\\w+)?";
        if (!includeEvents.matches(commaSeparatedRegex)) {
            return FormValidation.error("The included events list is not correctly written in a comma-separated list.");
        } 
        if (!excludeEvents.matches(commaSeparatedRegex)) {
            return FormValidation.error("The excluded events list is not correctly written in a comma-separated list.");
        }

        List<String> includedEventsList = (includeEvents.isEmpty()) ? new ArrayList<String>() : Arrays.asList(includeEvents.split(","));
        List<String> excludedEventsList = (excludeEvents.isEmpty()) ? new ArrayList<String>() : Arrays.asList(excludeEvents.split(","));

        List<String> allEvents = Arrays.asList(
            String.format("%s,%s,%s", SYSTEM_EVENTS, SECURITY_EVENTS, DEFAULT_EVENTS).split(","));
        if (!includedEventsList.stream().allMatch(allEvents::contains)) {
            return FormValidation.error("The included events list contains one or more unrecognized events.");
        }        
        if (!excludedEventsList.stream().allMatch(allEvents::contains)) {
            return FormValidation.error("The excluded events list contains one or more unrecognized events.");
        }

        Set<String> intersection = includedEventsList.stream()
            .distinct()
            .filter(excludedEventsList::contains)
            .collect(Collectors.toSet());
        
        if (intersection.size() > 0) {
            return FormValidation.error("The following events are in both the include and exclude lists: " + String.join(",", intersection));
        } 

        List<String> systemListToCheck = (emitSystemEvents) ? includedEventsList : excludedEventsList;
        List<String> securityListToCheck = (emitSecurityEvents) ? includedEventsList : excludedEventsList;

        if (systemListToCheck.stream().anyMatch(SYSTEM_EVENTS::contains)) {
            return FormValidation.warning("Redundant filtering: One or more system events have been toggled " + 
                ((emitSystemEvents) ? "on" : "off") + " as well as written in the " + ((emitSystemEvents) ? "include" : "exclude") + " list manually");
        }

        if (securityListToCheck.stream().anyMatch(SECURITY_EVENTS::contains)) {
            return FormValidation.warning("Redundant filtering: One or more security events have been toggled " + 
                ((emitSecurityEvents) ? "on" : "off") + " as well as written in the " + ((emitSecurityEvents) ? "include" : "exclude") + " list manually");
        }

        return FormValidation.ok("Your filtering configuration looks good!");
    }
}
