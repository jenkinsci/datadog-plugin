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

import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.*;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentHost;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentLogCollectionPort;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentPort;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentTraceCollectionPort;
import static org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeSite.DatadogIntakeSiteDescriptor.getSite;
import static org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogTextApiKey.DatadogTextApiKeyDescriptor.getDefaultKey;

import com.thoughtworks.xstream.annotations.XStreamConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractProject;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.Secret;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogApiConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogClientConfiguration;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntake;
import org.datadog.jenkins.plugins.datadog.configuration.api.intake.DatadogIntakeUrls;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogApiKey;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogCredentialsApiKey;
import org.datadog.jenkins.plugins.datadog.configuration.api.key.DatadogTextApiKey;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.conversion.PatternListConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.PolymorphicReflectionConverter;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
public class DatadogGlobalConfiguration extends GlobalConfiguration {

    private static final Logger logger = Logger.getLogger(DatadogGlobalConfiguration.class.getName());
    private static final String DISPLAY_NAME = "Datadog Plugin";

    public static final XStream2 XSTREAM;

    static {
        XSTREAM = new XStream2(XStream2.getDefaultDriver());
        XSTREAM.autodetectAnnotations(true);
    }

    // Event String constants
    public static final String SYSTEM_EVENTS = "ItemLocationChanged,"
            + "ComputerOnline,ComputerOffline,ComputerTemporarilyOnline,ComputerTemporarilyOffline,"
            + "ComputerLaunchFailure,ItemCreated,ItemDeleted,ItemUpdated,ItemCopied";
    public static final String SECURITY_EVENTS = "UserAuthenticated,UserFailedToAuthenticate,UserLoggedOut";
    public static final String DEFAULT_EVENTS = "BuildStarted,BuildAborted,BuildCompleted,SCMCheckout";

    // Env Var key to get the hostname from the Jenkins workers.
    public static final String DD_CI_HOSTNAME = "DD_CI_HOSTNAME";

    static final String REPORT_WITH_PROPERTY = "DATADOG_JENKINS_PLUGIN_REPORT_WITH";
    private static final String TARGET_TRACE_SERVICE_NAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_TRACE_SERVICE_NAME";
    private static final String HOSTNAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_HOSTNAME";
    private static final String DATADOG_APP_HOSTNAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_DATADOG_APP_HOSTNAME";
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
    private static final String INCLUDE_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_INCLUDE_EVENTS";
    private static final String EXCLUDE_EVENTS_PROPERTY = "DATADOG_JENKINS_PLUGIN_EXCLUDE_EVENTS";
    private static final String COLLECT_BUILD_LOGS_PROPERTY = "DATADOG_JENKINS_PLUGIN_COLLECT_BUILD_LOGS";
    private static final String REFRESH_DOGSTATSD_CLIENT_PROPERTY = "DATADOG_REFRESH_STATSD_CLIENT";
    private static final String CACHE_BUILD_RUNS_PROPERTY = "DATADOG_CACHE_BUILD_RUNS";
    private static final String USE_AWS_INSTANCE_HOSTNAME_PROPERTY = "DATADOG_USE_AWS_INSTANCE_HOSTNAME";
    private static final String SHOW_DATADOG_LINKS_ENV_PROPERTY = "DATADOG_JENKINS_PLUGIN_SHOW_DATADOG_LINKS";

    private static final String ENABLE_CI_VISIBILITY_PROPERTY = "DATADOG_JENKINS_PLUGIN_ENABLE_CI_VISIBILITY";
    private static final String CI_VISIBILITY_CI_INSTANCE_NAME_PROPERTY = "DATADOG_JENKINS_PLUGIN_CI_VISIBILITY_CI_INSTANCE_NAME";

    private static final String DEFAULT_CI_INSTANCE_NAME = "jenkins";
    private static final boolean DEFAULT_EMIT_SECURITY_EVENTS_VALUE = true;
    private static final boolean DEFAULT_EMIT_SYSTEM_EVENTS_VALUE = true;
    private static final boolean DEFAULT_COLLECT_BUILD_LOGS_VALUE = false;
    private static final boolean DEFAULT_COLLECT_BUILD_TRACES_VALUE = false;
    private static final boolean DEFAULT_RETRY_LOGS_VALUE = true;
    private static final boolean DEFAULT_REFRESH_DOGSTATSD_CLIENT_VALUE = false;
    private static final boolean DEFAULT_CACHE_BUILD_RUNS_VALUE = true;
    private static final boolean DEFAULT_USE_AWS_INSTANCE_HOSTNAME_VALUE = false;

    public static final String DATADOG_AGENT_CLIENT_TYPE = "DSD";
    public static final String DATADOG_API_CLIENT_TYPE = "HTTP";

    @XStreamConverter(PolymorphicReflectionConverter.class)
    private DatadogClientConfiguration datadogClientConfiguration;

    @XStreamConverter(PatternListConverter.class)
    private List<Pattern> excluded = null;

    @XStreamConverter(PatternListConverter.class)
    private List<Pattern> included = null;

    private String ciInstanceName = DEFAULT_CI_INSTANCE_NAME;
    private String hostname = null;
    private String datadogAppHostname = null;
    private String globalTagFile = null;
    private String globalTags = null;
    private String globalJobTags = null;
    private String includeEvents = null;
    private String excludeEvents = null;
    private boolean emitSecurityEvents = DEFAULT_EMIT_SECURITY_EVENTS_VALUE;
    private boolean emitSystemEvents = DEFAULT_EMIT_SYSTEM_EVENTS_VALUE;
    private boolean collectBuildLogs = DEFAULT_COLLECT_BUILD_LOGS_VALUE;
    private boolean enableCiVisibility = DEFAULT_COLLECT_BUILD_TRACES_VALUE;
    private transient boolean retryLogs = DEFAULT_RETRY_LOGS_VALUE; // TODO to be removed
    private boolean refreshDogstatsdClient = DEFAULT_REFRESH_DOGSTATSD_CLIENT_VALUE;
    private boolean cacheBuildRuns = DEFAULT_CACHE_BUILD_RUNS_VALUE;
    private boolean useAwsInstanceHostname = DEFAULT_USE_AWS_INSTANCE_HOSTNAME_VALUE;
    private boolean showDatadogLinks = true;

    @DataBoundConstructor
    public DatadogGlobalConfiguration() {
        load(); // Load the persisted global configuration
        loadEnvVariables(); // Load environment variables
    }

    @Override
    public synchronized void load() {
        XmlFile file = getConfigFile().exists() ? getConfigFile() : getLegacyConfigFile();
        if (!file.exists()) {
            return;
        }
        try {
            file.unmarshal(this);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load " + file, e);
        }
    }

    @Override
    protected XmlFile getConfigFile() {
        File rootDir = Jenkins.get().getRootDir();
        File currentConfigFile = new File(rootDir, getId() + "_v2.xml");
        return new XmlFile(XSTREAM, currentConfigFile);
    }

    // TODO remove this method and `load()` method override when we are confident that all users have migrated to the new config file
    private XmlFile getLegacyConfigFile() {
        File rootDir = Jenkins.get().getRootDir();
        File legacyConfigFile = new File(rootDir, getId() + ".xml");
        return new XmlFile(XSTREAM, legacyConfigFile);
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
    public void onStartup() {
        try {
            DatadogClient client = this.datadogClientConfiguration.createClient();
            ClientHolder.setClient(client);
        } catch (Exception e) {
            DatadogUtilities.logException(logger, Level.INFO, "Could not init Datadog client", e);
        }
    }

    public void loadEnvVariables() {
        // config values set manually in the UI take precedence over the ones provided via environment variables
        if (this.datadogClientConfiguration == null) {
            String clientType = System.getenv(REPORT_WITH_PROPERTY);
            if (DATADOG_AGENT_CLIENT_TYPE.equals(clientType)) {
                this.datadogClientConfiguration = new DatadogAgentConfiguration(
                        getDefaultAgentHost(), getDefaultAgentPort(), getDefaultAgentLogCollectionPort(), getDefaultAgentTraceCollectionPort());
            } else {
                DatadogIntake intake = DatadogIntake.getDefaultIntake();
                DatadogTextApiKey apiKey = new DatadogTextApiKey(getDefaultKey());
                this.datadogClientConfiguration = new DatadogApiConfiguration(intake, apiKey);
            }
        }

        String traceServiceNameVar = System.getenv(TARGET_TRACE_SERVICE_NAME_PROPERTY);
        if(StringUtils.isNotBlank(traceServiceNameVar)) {
            this.ciInstanceName = traceServiceNameVar;
        }

        String hostnameEnvVar = System.getenv(HOSTNAME_PROPERTY);
        if(StringUtils.isNotBlank(hostnameEnvVar)){
            this.hostname = hostnameEnvVar;
        }

        String datadogAppHostnameEnvVar = System.getenv(DATADOG_APP_HOSTNAME_PROPERTY);
        if (StringUtils.isNotBlank(datadogAppHostnameEnvVar)) {
            this.datadogAppHostname = datadogAppHostnameEnvVar;
        }

        String excludedEnvVar = System.getenv(EXCLUDED_PROPERTY);
        if (StringUtils.isNotBlank(excludedEnvVar)) {
            this.excluded = DatadogUtilities.cstrToList(excludedEnvVar, Pattern::compile);
        } else {
            // backwards compatibility
            excludedEnvVar = System.getenv(BLACKLIST_PROPERTY);
            if(StringUtils.isNotBlank(excludedEnvVar)){
                this.excluded = DatadogUtilities.cstrToList(excludedEnvVar, Pattern::compile);
            }
        }

        String includedEnvVar = System.getenv(INCLUDED_PROPERTY);
        if (StringUtils.isNotBlank(includedEnvVar)) {
            this.included = DatadogUtilities.cstrToList(excludedEnvVar, Pattern::compile);
        } else {
            // backwards compatibility
            includedEnvVar = System.getenv(WHITELIST_PROPERTY);
            if(StringUtils.isNotBlank(includedEnvVar)){
                this.included = DatadogUtilities.cstrToList(excludedEnvVar, Pattern::compile);
            }
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
            this.emitSecurityEvents = Boolean.parseBoolean(emitSecurityEventsEnvVar);
        }

        String emitSystemEventsEnvVar = System.getenv(EMIT_SYSTEM_EVENTS_PROPERTY);
        if(StringUtils.isNotBlank(emitSystemEventsEnvVar)){
            this.emitSystemEvents = Boolean.parseBoolean(emitSystemEventsEnvVar);
        }

        String includeEventsEnvVar = System.getenv(INCLUDE_EVENTS_PROPERTY);
        if(StringUtils.isNotBlank(includeEventsEnvVar)){
            this.includeEvents = includeEventsEnvVar;
        }

        String excludeEventsEnvVar = System.getenv(EXCLUDE_EVENTS_PROPERTY);
        if(StringUtils.isNotBlank(excludeEventsEnvVar)){
            this.excludeEvents = excludeEventsEnvVar;
        }

        String collectBuildLogsEnvVar = System.getenv(COLLECT_BUILD_LOGS_PROPERTY);
        if(StringUtils.isNotBlank(collectBuildLogsEnvVar)){
            this.collectBuildLogs = Boolean.parseBoolean(collectBuildLogsEnvVar);
        }

        String refreshDogstatsdClientEnvVar = System.getenv(REFRESH_DOGSTATSD_CLIENT_PROPERTY);
        if(StringUtils.isNotBlank(refreshDogstatsdClientEnvVar)){
            this.refreshDogstatsdClient = Boolean.parseBoolean(refreshDogstatsdClientEnvVar);
        }

        String cacheBuildRunsEnvVar = System.getenv(CACHE_BUILD_RUNS_PROPERTY);
        if(StringUtils.isNotBlank(cacheBuildRunsEnvVar)){
            this.cacheBuildRuns = Boolean.parseBoolean(cacheBuildRunsEnvVar);
        }

        String useAwsInstanceHostnameEnvVar = System.getenv(USE_AWS_INSTANCE_HOSTNAME_PROPERTY);
        if(StringUtils.isNotBlank(useAwsInstanceHostnameEnvVar)){
            this.useAwsInstanceHostname = Boolean.parseBoolean(useAwsInstanceHostnameEnvVar);
        }

        String showDatadogLinksEnvVar = System.getenv(SHOW_DATADOG_LINKS_ENV_PROPERTY);
        if(StringUtils.isNotBlank(useAwsInstanceHostnameEnvVar)){
            this.showDatadogLinks = Boolean.parseBoolean(showDatadogLinksEnvVar);
        }

        String enableCiVisibilityVar = System.getenv(ENABLE_CI_VISIBILITY_PROPERTY);
        if(StringUtils.isNotBlank(enableCiVisibilityVar)) {
            this.enableCiVisibility = Boolean.parseBoolean(enableCiVisibilityVar);
        }

        String ciVisibilityCiInstanceNameVar = System.getenv(CI_VISIBILITY_CI_INSTANCE_NAME_PROPERTY);
        if(StringUtils.isNotBlank(ciVisibilityCiInstanceNameVar)) {
            this.ciInstanceName = ciVisibilityCiInstanceNameVar;
        }
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
        return validateEventFilteringConfig(emitSecurityEvents, emitSystemEvents, includeEvents,
                excludeEvents);
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

    @RequirePOST
    public FormValidation doCheckCiInstanceName(@QueryParameter("ciInstanceName") final String ciInstanceName) {
        if (StringUtils.isBlank(ciInstanceName) && enableCiVisibility) {
            return FormValidation.error("CI Instance Name cannot be blank");
        }
        return FormValidation.ok();
    }

    @RequirePOST
    public FormValidation doCheckIncluded(@QueryParameter("included") final String included) {
        return doCheckPatterns(included);
    }

    @RequirePOST
    public FormValidation doCheckExcluded(@QueryParameter("excluded") final String excluded) {
        return doCheckPatterns(excluded);
    }

    private static FormValidation doCheckPatterns(String commaSeparatedPatterns) {
        List<String> patterns = DatadogUtilities.cstrToList(commaSeparatedPatterns);
        for (String pattern : patterns) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                return FormValidation.error(pattern + " is not a valid regular expression");
            }
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
     * Getter function for a human-readable plugin name, used in the configuration screen.
     *
     * @return a String containing the human-readable display name for this plugin.
     */
    @NonNull
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

            this.datadogClientConfiguration = req.bindJSON(DatadogClientConfiguration.class, formData.getJSONObject("datadogClientConfiguration"));

            try {
                final JSONObject ciVisibilityData = formData.getJSONObject("ciVisibilityData");
                if (ciVisibilityData != null && !ciVisibilityData.isNullObject()) {
                    this.datadogClientConfiguration.validateTracesConnection();

                    setEnableCiVisibility(true);

                    String ciInstanceName = ciVisibilityData.getString("ciInstanceName");
                    setCiInstanceName(StringUtils.isNotBlank(ciInstanceName) ? ciInstanceName : DEFAULT_CI_INSTANCE_NAME);
                }

            } catch (FormException ex) {
                //If it is the validation exception, we throw it to the next level.
                throw ex;
            } catch (Exception ex) {
                // We disable CI Visibility if there is an error parsing the CI Visibility configuration
                // because we don't want to prevent the user process the rest of the configuration.
                setEnableCiVisibility(false);
                setCiInstanceName(DEFAULT_CI_INSTANCE_NAME);
                DatadogUtilities.severe(logger, ex, "Failed to configure CI Visibility: " + ex.getMessage());
            }

            if(StringUtils.isNotBlank(this.getHostname()) && !DatadogUtilities.isValidHostname(this.getHostname())){
                throw new FormException("Your hostname is invalid, likely because it violates the format set in RFC 1123", "hostname");
            }

            setHostname(formData.getString("hostname"));

            String datadogAppHostname = formData.getString("datadogAppHostname");
            if (StringUtils.isNotBlank(datadogAppHostname) && !DatadogUtilities.isValidHostname(datadogAppHostname)) {
                throw new FormException("Your Datadog App hostname is invalid, likely because it violates the format set in RFC 1123", "datadogAppHostname");
            } else {
                setDatadogAppHostname(datadogAppHostname);
            }

            String excludedFormData = formData.getString("excluded");
            FormValidation excludedValidation = doCheckExcluded(excludedFormData);
            if (excludedValidation.kind == Kind.ERROR) {
                throw new FormException(excludedValidation.getMessage(), "excluded");
            } else {
                setExcluded(excludedFormData);
            }

            String includedFormData = formData.getString("included");
            FormValidation includedValidation = doCheckIncluded(includedFormData);
            if (includedValidation.kind == Kind.ERROR) {
                throw new FormException(includedValidation.getMessage(), "included");
            } else {
                setIncluded(includedFormData);
            }

            setGlobalTagFile(formData.getString("globalTagFile"));
            setGlobalTags(formData.getString("globalTags"));
            setGlobalJobTags(formData.getString("globalJobTags"));
            setRefreshDogstatsdClient(formData.getBoolean("refreshDogstatsdClient"));
            setCacheBuildRuns(formData.getBoolean("cacheBuildRuns"));
            setUseAwsInstanceHostname(formData.getBoolean("useAwsInstanceHostname"));

            boolean emitSecurityEvents = formData.getBoolean("emitSecurityEvents");
            boolean emitSystemEvents = formData.getBoolean("emitSystemEvents");
            String includeEvents = formData.getString("includeEvents");
            String excludeEvents = formData.getString("excludeEvents");
            FormValidation configStatus = validateEventFilteringConfig(emitSecurityEvents, emitSystemEvents, includeEvents, excludeEvents);
            if (configStatus.kind == Kind.ERROR) {
                String message = configStatus.getMessage();
                String formField = !message.contains("included") ? "excludeEvents" : "includeEvents";
                throw new FormException(message, formField);
            }

            setEmitSecurityEvents(emitSecurityEvents);
            setEmitSystemEvents(emitSystemEvents);
            setIncludeEvents(includeEvents);
            setExcludeEvents(excludeEvents);

            setCollectBuildLogs(formData.getBoolean("collectBuildLogs"));
            if (this.collectBuildLogs) {
                this.datadogClientConfiguration.validateLogsConnection();
            }

            DatadogClient client = this.datadogClientConfiguration.createClient();
            ClientHolder.setClient(client);

            // Persist global configuration information
            save();
            return true;
        } catch(FormException e) {
            throw e;
        } catch(Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to save configuration");
            return false;
        }
    }

    public DatadogClientConfiguration getDatadogClientConfiguration() {
        return datadogClientConfiguration;
    }

    public void setDatadogClientConfiguration(DatadogClientConfiguration datadogClientConfiguration) {
        this.datadogClientConfiguration = datadogClientConfiguration;
    }

    /**
     * Getter function for the ciInstanceName global configuration.
     *
     * @return a String containing the ciInstanceName global configuration.
     */
    public String getCiInstanceName() {
        return this.ciInstanceName;
    }

    /**
     * Setter function for the ciInstanceName global configuration.
     *
     * @param ciInstanceName = A string containing the CI Instance Name
     */
    public void setCiInstanceName(String ciInstanceName) {
        this.ciInstanceName = ciInstanceName;
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
    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    public String getDatadogAppHostname() {
        return datadogAppHostname;
    }

    public void setDatadogAppHostname(String datadogAppHostname) {
        this.datadogAppHostname = datadogAppHostname;
    }

    public boolean isJobExcluded(@Nonnull final String jobName) {
        if (excluded == null || excluded.isEmpty()) {
            return false;
        }
        for (Pattern pattern : excluded) {
            Matcher matcher = pattern.matcher(jobName);
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Getter function for the excluded global configuration, containing
     * a comma-separated list of jobs to exclude from monitoring.
     *
     * @return a String array containing the excluded global configuration.
     */
    public String getExcluded() {
        return DatadogUtilities.listToCstr(excluded, Pattern::toString);
    }

    /**
     * Setter function for the excluded jobs global configuration,
     * accepting a comma-separated string of jobs.
     *
     * @param jobs - a comma-separated list of jobs to exclude from monitoring.
     */
    public void setExcluded(final String jobs) {
        this.excluded = DatadogUtilities.cstrToList(jobs, Pattern::compile);
    }

    public boolean isJobIncluded(@Nonnull final String jobName) {
        if (included == null || included.isEmpty()) {
            return true;
        }
        for (Pattern pattern : included) {
            Matcher matcher = pattern.matcher(jobName);
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Getter function for the included global configuration, containing
     * a comma-separated list of jobs to include for monitoring.
     *
     * @return a String array containing the included global configuration.
     */
    public String getIncluded() {
        return DatadogUtilities.listToCstr(included, Pattern::toString);
    }

    /**
     * Setter function for the includedd global configuration,
     * accepting a comma-separated string of jobs.
     *
     * @param jobs - a comma-separated list of jobs to include for monitoring.
     */
    public void setIncluded(final String jobs) {
        this.included = DatadogUtilities.cstrToList(jobs, Pattern::compile);
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
    public void setGlobalJobTags(String globalJobTags) {
        this.globalJobTags = globalJobTags;
    }

    /**
     * @deprecated This method is here to ensure backward compatibility
     */
    @Deprecated
    public boolean isRetryLogs() {
        return retryLogs;
    }

    /**
     * @deprecated This method is here to ensure backward compatibility
     */
    @Deprecated
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
    public void setUseAwsInstanceHostname(boolean useAwsInstanceHostname) {
        this.useAwsInstanceHostname = useAwsInstanceHostname;
    }

    public boolean isShowDatadogLinks() {
        return showDatadogLinks;
    }

    public void setShowDatadogLinks(boolean showDatadogLinks) {
        this.showDatadogLinks = showDatadogLinks;
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
    public void setEmitSystemEvents(boolean emitSystemEvents) {
        this.emitSystemEvents = emitSystemEvents;
    }

    /**
     * Setter function for the included global configuration,
     * accepting a comma-separated string of events.
     *
     * @param events - a comma-separated list of events to include for sending to agent.
     */
    public void setIncludeEvents(String events) {
        this.includeEvents = events;
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
    public void setExcludeEvents(String events) {
        this.excludeEvents = events;
    }

    /**
     * Getter function for the included global configuration, containing
     * a comma-separated list of events not to send.
     *
     * @return a String array containing the events included global configuration.
     */
    public String getExcludeEvents() {
        return excludeEvents;
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
    public void setCollectBuildLogs(boolean collectBuildLogs) {
        this.collectBuildLogs = collectBuildLogs;
    }

    /**
     * @return - A {@link Boolean} indicating if the user has configured Datadog to enable CI Visibility.
     */
    public boolean getEnableCiVisibility() {
        return this.enableCiVisibility;
    }

    /**
     * Set the checkbox in the UI, used for Jenkins data binding to enable CI Visibility
     *
     * @param enableCiVisibility - The checkbox status (checked/unchecked)
     */
    public void setEnableCiVisibility(boolean enableCiVisibility) {
        this.enableCiVisibility = enableCiVisibility;
    }

    /**
     * @see #doTestFilteringConfig
     */
    private FormValidation validateEventFilteringConfig(boolean emitSecurityEvents, boolean emitSystemEvents,
                                                        String includeEvents, String excludeEvents) {
        String commaSeparatedRegex = "((\\w+,)*\\w+)?";
        if (!includeEvents.matches(commaSeparatedRegex)) {
            return FormValidation.error("The included events list is not correctly written in a comma-separated list.");
        }
        if (!excludeEvents.matches(commaSeparatedRegex)) {
            return FormValidation.error("The excluded events list is not correctly written in a comma-separated list.");
        }

        List<String> includedEventsList = (includeEvents.isEmpty()) ? Collections.emptyList() : Arrays.asList(includeEvents.split(","));
        List<String> excludedEventsList = (excludeEvents.isEmpty()) ? Collections.emptyList() : Arrays.asList(excludeEvents.split(","));

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

        if (!intersection.isEmpty()) {
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

    public Collection<DatadogClientConfiguration.DatadogClientConfigurationDescriptor> getDatadogClientConfigOptions() {
        return DatadogClientConfiguration.DatadogClientConfigurationDescriptor.all();
    }

    /* ****************************************************************************************************************
     * The fields/methods below are deprecated.
     * They are here only to ensure backward compatibility.
     * Do not use them.
     * ***************************************************************************************************************/

    /** @deprecated use {@link #ciInstanceName} */
    @Deprecated
    private String traceServiceName;
    /** @deprecated use {@link #excluded} */
    @Deprecated
    private String blacklist;
    /** @deprecated use {@link #included} */
    @Deprecated
    private String whitelist;
    /** @deprecated use {@link #enableCiVisibility} */
    @Deprecated
    boolean collectBuildTraces;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    private String reportWith;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    private String targetApiURL;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    private String targetLogIntakeURL;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    private String targetWebhookIntakeURL;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    private Secret targetApiKey;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    @SuppressWarnings("lgtm[jenkins/plaintext-storage]") // not the actual key, but the ID of Jenkins credentials
    private String targetCredentialsApiKey;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    private String targetHost;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    private Integer targetPort;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    private Integer targetLogCollectionPort;
    /** @deprecated use {@link #datadogClientConfiguration} */
    @Deprecated
    private Integer targetTraceCollectionPort;

    /** @deprecated use {@link #setCiInstanceName(String)} */
    public void setTraceServiceName(String traceServiceName) {
        this.traceServiceName = traceServiceName;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setExcluded(String)} */
    public void setBlacklist(String blacklist) {
        this.blacklist = blacklist;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setIncluded(String)} */
    public void setWhitelist(String whitelist) {
        this.whitelist = whitelist;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setEnableCiVisibility(boolean)} */
    public void setCollectBuildTraces(boolean collectBuildTraces) {
        this.collectBuildTraces = collectBuildTraces;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setReportWith(String reportWith) {
        this.reportWith = reportWith;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setTargetApiURL(String targetApiURL) {
        this.targetApiURL = targetApiURL;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setTargetLogIntakeURL(String targetLogIntakeURL) {
        this.targetLogIntakeURL = targetLogIntakeURL;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setTargetWebhookIntakeURL(String targetWebhookIntakeURL) {
        this.targetWebhookIntakeURL = targetWebhookIntakeURL;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setTargetApiKey(Secret targetApiKey) {
        this.targetApiKey = targetApiKey;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setTargetCredentialsApiKey(String targetCredentialsApiKey) {
        this.targetCredentialsApiKey = targetCredentialsApiKey;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setTargetLogCollectionPort(Integer targetLogCollectionPort) {
        this.targetLogCollectionPort = targetLogCollectionPort;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated use {@link #setDatadogClientConfiguration(DatadogClientConfiguration)} */
    public void setTargetTraceCollectionPort(Integer targetTraceCollectionPort) {
        this.targetTraceCollectionPort = targetTraceCollectionPort;
        // Configuration-as-Code plugin does not call readResolve() so it has to be done manually
        // This also ensures backward compatibility for programmatic configuration with Groovy
        readResolve();
    }

    /** @deprecated this configuration property has been removed */
    public void setEmitConfigChangeEvents(boolean ignored) {
        // this method is here only to avoid errors if someone tries to call it from Groovy configuration scripts
    }

    /** @deprecated use {@link #getCiInstanceName()} */
    @Deprecated
    public String getTraceServiceName() {
        return ciInstanceName;
    }

    /** @deprecated use {@link #getExcluded()} */
    @Deprecated
    public String getBlacklist() {
        return DatadogUtilities.listToCstr(excluded, Pattern::toString);
    }

    /** @deprecated use {@link #getIncluded()} */
    @Deprecated
    public String getWhitelist() {
        return DatadogUtilities.listToCstr(included, Pattern::toString);
    }

    /** @deprecated use {@link #getEnableCiVisibility()} */
    @Deprecated
    public boolean isCollectBuildTraces() {
        return enableCiVisibility;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public String getReportWith() {
        if (datadogClientConfiguration instanceof DatadogAgentConfiguration) {
            return DATADOG_AGENT_CLIENT_TYPE;
        }
        if (datadogClientConfiguration instanceof DatadogApiConfiguration) {
            return DATADOG_API_CLIENT_TYPE;
        }
        return null;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public String getTargetApiURL() {
        if (datadogClientConfiguration instanceof DatadogApiConfiguration) {
            DatadogApiConfiguration apiConfiguration = (DatadogApiConfiguration) datadogClientConfiguration;
            DatadogIntake intake = apiConfiguration.getIntake();
            return intake.getApiUrl();
        }
        return null;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public String getTargetLogIntakeURL() {
        if (datadogClientConfiguration instanceof DatadogApiConfiguration) {
            DatadogApiConfiguration apiConfiguration = (DatadogApiConfiguration) datadogClientConfiguration;
            DatadogIntake intake = apiConfiguration.getIntake();
            return intake.getLogsUrl();
        }
        return null;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public String getTargetWebhookIntakeURL() {
        if (datadogClientConfiguration instanceof DatadogApiConfiguration) {
            DatadogApiConfiguration apiConfiguration = (DatadogApiConfiguration) datadogClientConfiguration;
            DatadogIntake intake = apiConfiguration.getIntake();
            return intake.getWebhooksUrl();
        }
        return null;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public Secret getTargetApiKey() {
        if (datadogClientConfiguration instanceof DatadogApiConfiguration) {
            DatadogApiConfiguration apiConfiguration = (DatadogApiConfiguration) datadogClientConfiguration;
            DatadogApiKey apiKey = apiConfiguration.getApiKey();
            if (apiKey instanceof DatadogTextApiKey) {
                DatadogTextApiKey textApiKey = (DatadogTextApiKey) apiKey;
                return textApiKey.getKey();
            }
        }
        return null;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public String getTargetCredentialsApiKey() {
        if (datadogClientConfiguration instanceof DatadogApiConfiguration) {
            DatadogApiConfiguration apiConfiguration = (DatadogApiConfiguration) datadogClientConfiguration;
            DatadogApiKey apiKey = apiConfiguration.getApiKey();
            if (apiKey instanceof DatadogCredentialsApiKey) {
                DatadogCredentialsApiKey credentialsApiKey = (DatadogCredentialsApiKey) apiKey;
                return credentialsApiKey.getCredentialsId();
            }
        }
        return null;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public String getTargetHost() {
        if (datadogClientConfiguration instanceof DatadogAgentConfiguration) {
            DatadogAgentConfiguration agentConfiguration = (DatadogAgentConfiguration) datadogClientConfiguration;
            return agentConfiguration.getAgentHost();
        }
        return null;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public Integer getTargetPort() {
        if (datadogClientConfiguration instanceof DatadogAgentConfiguration) {
            DatadogAgentConfiguration agentConfiguration = (DatadogAgentConfiguration) datadogClientConfiguration;
            return agentConfiguration.getAgentPort();
        }
        return null;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public Integer getTargetLogCollectionPort() {
        if (datadogClientConfiguration instanceof DatadogAgentConfiguration) {
            DatadogAgentConfiguration agentConfiguration = (DatadogAgentConfiguration) datadogClientConfiguration;
            return agentConfiguration.getAgentLogCollectionPort();
        }
        return null;
    }

    /** @deprecated use {@link #getDatadogClientConfiguration()} */
    @Deprecated
    public Integer getTargetTraceCollectionPort() {
        if (datadogClientConfiguration instanceof DatadogAgentConfiguration) {
            DatadogAgentConfiguration agentConfiguration = (DatadogAgentConfiguration) datadogClientConfiguration;
            return agentConfiguration.getAgentTraceCollectionPort();
        }
        return null;
    }

    /** @deprecated this configuration property has been removed */
    @Deprecated
    public boolean getEmitConfigChangeEvents() {
        // this method is here only to avoid errors if someone tries to call it from Groovy configuration scripts
        return false;
    }

    /**
     * Maintains backwards compatibility. Invoked by XStream when this object is deserialized.
     */
    @SuppressWarnings("UnusedReturnValue")
    protected Object readResolve() {
        if (this.collectBuildTraces) {
            this.enableCiVisibility = true;
        }
        if (StringUtils.isNotBlank(this.traceServiceName)) {
            this.ciInstanceName = this.traceServiceName;
        }
        if (StringUtils.isNotBlank(this.blacklist)) {
            this.excluded = DatadogUtilities.cstrToList(this.blacklist, Pattern::compile);
        }
        if (StringUtils.isNotBlank(this.whitelist)) {
            this.included = DatadogUtilities.cstrToList(this.whitelist, Pattern::compile);
        }
        if (DATADOG_AGENT_CLIENT_TYPE.equals(reportWith)) {
            this.datadogClientConfiguration = new DatadogAgentConfiguration(this.targetHost, this.targetPort, this.targetLogCollectionPort, this.targetTraceCollectionPort);
        }
        if (DATADOG_API_CLIENT_TYPE.equals(reportWith)) {
            DatadogIntakeUrls intake = new DatadogIntakeUrls(this.targetApiURL, this.targetLogIntakeURL, this.targetWebhookIntakeURL);
            DatadogApiKey apiKey;
            if (StringUtils.isNotBlank(this.targetCredentialsApiKey)) {
                apiKey = new DatadogCredentialsApiKey(this.targetCredentialsApiKey);
            } else if (this.targetApiKey != null) {
                apiKey = new DatadogTextApiKey(this.targetApiKey);
            } else {
                apiKey = null;
            }
            this.datadogClientConfiguration = new DatadogApiConfiguration(intake, apiKey);
        }
        return this;
    }
}
