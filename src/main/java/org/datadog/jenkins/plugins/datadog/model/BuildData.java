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

package org.datadog.jenkins.plugins.datadog.model;

import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_DATE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_EMAIL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_AUTHOR_NAME;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_DATE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_EMAIL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_COMMITTER_NAME;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_MESSAGE;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.GIT_BRANCH;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isUserSuppliedGit;

import com.cloudbees.plugins.credentials.CredentialsParameterValue;
import hudson.EnvVars;
import hudson.matrix.MatrixProject;
import hudson.model.BooleanParameterValue;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.TextParameterValue;
import hudson.model.User;
import hudson.tasks.Mailer;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.branch.MultiBranchProject;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.traces.BuildConfigurationParser;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanManager;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl;

public class BuildData implements Serializable {

    private static final long serialVersionUID = 1L;

    private static transient final Logger LOGGER = Logger.getLogger(BuildData.class.getName());
    private String buildNumber;
    private String buildId;
    private String buildUrl;
    private Map<String, String> buildParameters = new HashMap<>();
    private String charsetName;
    private String nodeName;
    private String jobName;
    private Map<String, String> buildConfigurations;
    private String buildTag;
    @Nullable
    private String upstreamBuildTag;
    private String jenkinsUrl;
    private String executorNumber;
    private String javaHome;
    private String workspace;
    // Branch contains either env variable - SVN_REVISION or CVS_BRANCH or GIT_BRANCH
    private String branch;
    private String gitUrl;
    private String gitCommit;
    private String gitMessage;
    private String gitAuthorName;
    private String gitAuthorEmail;
    private String gitAuthorDate;
    private String gitCommitterName;
    private String gitCommitterEmail;
    private String gitCommitterDate;
    private String gitDefaultBranch;
    private String gitTag;

    // Environment variable from the promoted build plugin
    // - See https://plugins.jenkins.io/promoted-builds
    // - See https://wiki.jenkins.io/display/JENKINS/Promoted+Builds+Plugin
    private String promotedUrl;
    private String promotedJobName;
    private String promotedNumber;
    private String promotedId;
    private String promotedTimestamp;
    private String promotedUserName;
    private String promotedUserId;
    private String promotedJobFullName;

    private String result;
    private boolean isCompleted;
    private boolean isBuilding;
    private String hostname;
    private String userId;
    private String userEmail;
    private Map<String, Set<String>> tags;

    private Long startTime;
    private Long endTime;
    private Long duration;
    private Long millisInQueue;
    private Long propagatedMillisInQueue;

    /**
     * Monotonically increasing "version" of the build data.
     * As the pipeline progresses, it can be reported to the backend more than once:
     * <ul>
     * <li>when it starts executing</li>
     * <li>when git info or node info become available</li>
     * <li>when it finishes.</li>
     * </ul>
     * The backend needs version to determine the relative order of these multiple events.
     */
    private Integer version;
    private String traceId;
    private String spanId;

    private String upstreamPipelineUrl;
    private Long upstreamPipelineTraceId;

    public BuildData(Run<?, ?> run, @Nullable TaskListener listener) throws IOException, InterruptedException {
        if (run == null) {
            return;
        }
        EnvVars envVars = getEnvVars(run, listener);

        this.tags = DatadogUtilities.getBuildTags(run, envVars);

        this.buildUrl = envVars.get("BUILD_URL");

        BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
        if (buildSpanAction != null) {
            if (this.buildUrl == null) {
                this.buildUrl = buildSpanAction.getBuildUrl();
            }
            this.version = buildSpanAction.getAndIncrementVersion();
        }

        // Populate instance using environment variables.
        populateEnvVariables(envVars);

        // Populate instance using Git info if possible.
        // Set all Git commit related variables.
        populateGitVariables(run);

        // Set Jenkins Url
        this.jenkinsUrl = DatadogUtilities.getJenkinsUrl();
        // Set UserId
        this.userId = getUserId(run);
        // Set UserEmail
        if(StringUtils.isEmpty(getUserEmail(""))){
            this.userEmail = getUserEmailByUserId(getUserId());
        }

        // Set Result and completed status
        Result runResult = run.getResult();
        if (runResult != null) {
            this.result = runResult.toString();
            this.isCompleted = runResult.completeBuild;
        } else {
            this.result = null;
            this.isCompleted = false;
        }
        this.isBuilding = run.isBuilding();

        // Set StartTime, EndTime and Duration
        this.startTime = run.getStartTimeInMillis();
        long durationInMs = run.getDuration();
        if (durationInMs == 0 && startTime != 0) {
            durationInMs = System.currentTimeMillis() - startTime;
        }
        this.duration = durationInMs;
        if (duration != 0 && startTime != 0) {
            // end time will be ignored for in-progress pipelines
            this.endTime = startTime + duration;
        }

        // Set Build Number
        this.buildNumber = String.valueOf(run.getNumber());

        final PipelineNodeInfoAction pipelineInfo = run.getAction(PipelineNodeInfoAction.class);
        if (pipelineInfo != null && pipelineInfo.getNodeName() != null) {
            this.nodeName = pipelineInfo.getNodeName();
        } else {
            this.nodeName = envVars.get("NODE_NAME");
        }

        if (pipelineInfo != null && pipelineInfo.getNodeHostname() != null) {
            // using the hostname determined during a pipeline step execution
            // (this option is only available for pipelines, and not for freestyle builds)
            this.hostname = pipelineInfo.getNodeHostname();
        } else if (DatadogUtilities.isMainNode(nodeName)) {
            // the job is run on the master node, checking plugin config and locally available info.
            // (nodeName == null) condition is there to preserve existing behavior
            this.hostname = DatadogUtilities.getHostname(envVars);
        } else if (envVars.containsKey(DatadogGlobalConfiguration.DD_CI_HOSTNAME)) {
            // the job is run on an agent node, querying DD_CI_HOSTNAME set explicitly on agent
            this.hostname = envVars.get(DatadogGlobalConfiguration.DD_CI_HOSTNAME);
        } else {
            // the job is run on an agent node, querying HOSTNAME set implicitly on agent
            this.hostname = envVars.get("HOSTNAME");
        }

        if (pipelineInfo != null && pipelineInfo.getWorkspace() != null) {
            this.workspace = pipelineInfo.getWorkspace();
        } else {
            this.workspace = envVars.get("WORKSPACE");
        }

        PipelineQueueInfoAction action = run.getAction(PipelineQueueInfoAction.class);
        if (action != null) {
            this.millisInQueue = action.getQueueTimeMillis();
            this.propagatedMillisInQueue = action.getPropagatedQueueTimeMillis();
        }

        // Save charset canonical name
        this.charsetName = run.getCharset().name();

        this.jobName = normalizeJobName(getJobName(run, envVars));
        this.buildConfigurations = BuildConfigurationParser.parseConfigurations(run);

        // Set Jenkins Url
        String jenkinsUrl = DatadogUtilities.getJenkinsUrl();
        if("unknown".equals(jenkinsUrl) && envVars != null && envVars.get("JENKINS_URL") != null
                && !envVars.get("JENKINS_URL").isEmpty()) {
            jenkinsUrl = envVars.get("JENKINS_URL");
        }
        this.jenkinsUrl = jenkinsUrl;

        // Build parameters
        populateBuildParameters(run);

        // Set Tracing IDs
        TraceSpan.TraceSpanContext buildSpanContext = BuildSpanManager.get().get(getBuildTag(""));
        if(buildSpanContext !=null) {
            this.traceId = Long.toUnsignedString(buildSpanContext.getTraceId());
            this.spanId = Long.toUnsignedString(buildSpanContext.getSpanId());
        }

        populateUpstreamPipelineData(run, envVars);
    }

    private void populateUpstreamPipelineData(Run<?, ?> run, EnvVars envVars) {
        CauseAction causeAction = run.getAction(CauseAction.class);
        if (causeAction == null) {
            return;
        }
        Cause.UpstreamCause upstreamCause = causeAction.findCause(Cause.UpstreamCause.class);
        if (upstreamCause == null) {
            return;
        }

        String hudsonUrl = envVars.get("HUDSON_URL");
        String upstreamUrl = upstreamCause.getUpstreamUrl();
        int upstreamBuild = upstreamCause.getUpstreamBuild();
        if (hudsonUrl != null && upstreamUrl != null) {
            upstreamPipelineUrl = hudsonUrl + upstreamUrl + upstreamBuild + "/";
        }

        String upstreamProject = upstreamCause.getUpstreamProject();
        if (upstreamProject != null) {
            upstreamBuildTag = "jenkins-" + upstreamProject.replace('/', '-') + "-" + upstreamBuild;
            TraceSpan.TraceSpanContext upstreamPipelineContext = BuildSpanManager.get().get(upstreamBuildTag);
            if (upstreamPipelineContext == null) {
                BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
                if (buildSpanAction != null) {
                    upstreamPipelineContext = buildSpanAction.getUpstreamSpanContext();
                }
            }

            if (upstreamPipelineContext != null) {
                upstreamPipelineTraceId = upstreamPipelineContext.getTraceId();
            }
        }
    }

    private static EnvVars getEnvVars(Run run, TaskListener listener) throws IOException, InterruptedException {
        EnvVars mergedVars = new EnvVars();

        List<EnvActionImpl> envActions = run.getActions(EnvActionImpl.class);
        for (EnvActionImpl envAction : envActions) {
            Map<String, String> overriddenEnvironment = envAction.getOverriddenEnvironment();
            mergedVars.putAll(overriddenEnvironment);
        }

        Map<String, String> envVars;
        if(listener != null){
            envVars = run.getEnvironment(listener);
        }else{
            envVars = run.getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
        }
        mergedVars.putAll(envVars);

        return mergedVars;
    }

    @Nonnull
    private static String getJobName(Run<?, ?> run, EnvVars envVars) {
        Job<?, ?> job = run.getParent();
        ItemGroup<?> jobParent = job.getParent();
        if (jobParent instanceof MultiBranchProject || jobParent instanceof MatrixProject) {
            // For certain builds the job name is too specific,
            // and we have to use job's parent to get a name that is generic enough:

            // 1. In case of multi-branch projects (Multibranch Pipelines and Organization Folders)
            // job corresponds to a specific branch.
            // We don't want pipeline names to contain branches,
            // instead we want the same pipeline executed on different branches to have the same name.
            // So instead of the job (which corresponds to a specific branch)
            // we use its parent (which encapsulates all branches for that pipeline).

            // 2. In case of matrix projects job corresponds to a specific combination of parameters (MatrixConfiguration).
            // We use matrix configuration parent, which is the matrix project, to get the project name

            // We don't want to follow this logic for other cases,
            // because job parent can be a folder, in which case we will use folder name as pipeline name, which is incorrect.
            String jobParentName = jobParent.getFullName();
            if (StringUtils.isNotBlank(jobParentName)) {
                return jobParentName;
            }
        }

        String jobName = job.getFullName();
        if (StringUtils.isNotBlank(jobName)) {
            return jobName;
        }

        if (envVars != null) {
            String envJobName = envVars.get("JOB_NAME");
            if (StringUtils.isNotBlank(envJobName)) {
                return envJobName;
            }
        }

        return "unknown";
    }

    @Nonnull
    private static String normalizeJobName(@Nonnull String jobName) {
        return jobName.replaceAll("»", "/").replaceAll(" ", "");
    }

    private void populateBuildParameters(Run<?,?> run) {
        // Build parameters can be defined via Jenkins UI
        // or via Jenkinsfile (https://www.jenkins.io/doc/book/pipeline/syntax/#parameters)
        try {
            final ParametersAction parametersAction = run.getAction(ParametersAction.class);
            if(parametersAction == null){
                return;
            }

            final List<ParameterValue> parameters = parametersAction.getAllParameters();
            if(parameters == null || parameters.isEmpty()){
                return;
            }

            for(final ParameterValue parameter : parameters) {
                // Only support parameters as string (only single line), boolean and credentials for the moment.
                // Credentials parameters are safe because the value will show the credential ID, not the secret itself.
                // Choice parameters are treated as string parameters internally.
                if((parameter instanceof StringParameterValue && !(parameter instanceof TextParameterValue))
                        || parameter instanceof BooleanParameterValue
                        || parameter instanceof CredentialsParameterValue) {
                    this.buildParameters.put(parameter.getName(), String.valueOf(parameter.getValue()));
                }
            }
        } catch (Throwable ex) {
            DatadogUtilities.severe(LOGGER, ex, "Failed to populate Jenkins build parameters.");
        }
    }

    private void populateEnvVariables(EnvVars envVars){
        if (envVars == null) {
            return;
        }
        this.buildId = envVars.get("BUILD_ID");

        String envBuildTag = envVars.get("BUILD_TAG");
        if (StringUtils.isNotBlank(envBuildTag)) {
            this.buildTag = envBuildTag;
        } else {
            this.buildTag = "jenkins-" + envVars.get("JOB_NAME") + "-" + envVars.get("BUILD_NUMBER");
        }

        this.executorNumber = envVars.get("EXECUTOR_NUMBER");
        this.javaHome = envVars.get("JAVA_HOME");
        if (isGit(envVars)) {
            this.branch = GitUtils.resolveGitBranch(envVars);
            this.gitUrl = GitUtils.resolveGitRepositoryUrl(envVars);
            this.gitCommit = GitUtils.resolveGitCommit(envVars);
            this.gitTag = GitUtils.resolveGitTag(envVars);

            // Git data supplied by the user has prevalence. We set them first.
            // Only the data that has not been set will be updated later.
            // If any value is not provided, we maintained the original value if any.
            this.gitMessage = envVars.get(DD_GIT_COMMIT_MESSAGE, this.gitMessage);
            this.gitAuthorName = envVars.get(DD_GIT_COMMIT_AUTHOR_NAME, this.gitAuthorName);
            this.gitAuthorEmail = envVars.get(DD_GIT_COMMIT_AUTHOR_EMAIL, this.gitAuthorEmail);
            this.gitAuthorDate = envVars.get(DD_GIT_COMMIT_AUTHOR_DATE, this.gitAuthorDate);
            this.gitCommitterName = envVars.get(DD_GIT_COMMIT_COMMITTER_NAME, this.gitCommitterName);
            this.gitCommitterEmail = envVars.get(DD_GIT_COMMIT_COMMITTER_EMAIL, this.gitCommitterEmail);
            this.gitCommitterDate = envVars.get(DD_GIT_COMMIT_COMMITTER_DATE, this.gitCommitterDate);

        } else if (envVars.get("CVS_BRANCH") != null) {
            this.branch = envVars.get("CVS_BRANCH");
        }
        this.promotedUrl = envVars.get("PROMOTED_URL");
        this.promotedJobName = envVars.get("PROMOTED_JOB_NAME");
        this.promotedNumber = envVars.get("PROMOTED_NUMBER");
        this.promotedId = envVars.get("PROMOTED_ID");
        this.promotedTimestamp = envVars.get("PROMOTED_TIMESTAMP");
        this.promotedUserName = envVars.get("PROMOTED_USER_NAME");
        this.promotedUserId = envVars.get("PROMOTED_USER_ID");
        this.promotedJobFullName = envVars.get("PROMOTED_JOB_FULL_NAME");
    }

    /**
     * Populate git commit related information in the BuildData instance.
     * The data is retrieved from {@link GitRepositoryAction} and {@link GitCommitAction} that are associated with the build.
     * The actions are populated from two main sources:
     * <ol>
     *     <li>Environment variables of specific pipeline steps:
     *     pipeline object has its own set of env variables, but it is minimal;
     *     the whole set of env variables
     *     (including those that are set by Jenkins Git Plugin or manually by the pipeline authors)
     *     is only available for individual pipeline steps.
     *     That is why in {@link org.datadog.jenkins.plugins.datadog.listeners.DatadogStepListener}
     *     we examine the full set of env vars to see if we can extract any git-related info</li>
     *     <li>Git repositories that were checked out during pipeline execution:
     *     {@link org.datadog.jenkins.plugins.datadog.listeners.DatadogSCMListener} is notified of every source-code checkout.
     *     If the checked out repo is a git repository, we create a git client and examine repository metadata</li>
     * </ol>
     */
    private void populateGitVariables(Run<?,?> run) {
        GitRepositoryAction gitRepositoryAction = run.getAction(GitRepositoryAction.class);
        populateRepositoryInfo(gitRepositoryAction);

        GitCommitAction gitCommitAction = run.getAction(GitCommitAction.class);
        populateCommitInfo(gitCommitAction);
    }

    /**
     * Populate the information related to the commit (message, author and committer) based on the GitCommitAction
     * only if the user has not set the value manually.
     */
    private void populateCommitInfo(GitCommitAction gitCommitAction) {
        if(gitCommitAction != null) {
            // If any value is not empty, it means that
            // the user supplied the value manually
            // via environment variables.

            String existingCommit = getGitCommit("");
            if (!existingCommit.isEmpty() && !existingCommit.equals(gitCommitAction.getCommit())) {
                // user-supplied commit is different
                return;
            }

            if(existingCommit.isEmpty()){
                this.gitCommit = gitCommitAction.getCommit();
            }

            if(getGitTag("").isEmpty()){
                this.gitTag = gitCommitAction.getTag();
            }

            if(getGitMessage("").isEmpty()){
                this.gitMessage = gitCommitAction.getMessage();
            }

            if(getGitAuthorName("").isEmpty()){
                this.gitAuthorName = gitCommitAction.getAuthorName();
            }

            if(getGitAuthorEmail("").isEmpty()) {
                this.gitAuthorEmail = gitCommitAction.getAuthorEmail();
            }

            if(getGitAuthorDate("").isEmpty()){
                this.gitAuthorDate = gitCommitAction.getAuthorDate();
            }

            if(getGitCommitterName("").isEmpty()){
                this.gitCommitterName = gitCommitAction.getCommitterName();
            }

            if(getGitCommitterEmail("").isEmpty()){
                this.gitCommitterEmail = gitCommitAction.getCommitterEmail();
            }

            if(getGitCommitterDate("").isEmpty()){
                this.gitCommitterDate = gitCommitAction.getCommitterDate();
            }
        }
    }

    private void populateRepositoryInfo(GitRepositoryAction gitRepositoryAction) {
        if (gitRepositoryAction != null) {
            if (gitUrl != null && !gitUrl.isEmpty() && !gitUrl.equals(gitRepositoryAction.getRepositoryURL())) {
                // user-supplied URL is different
                return;
            }

            if (gitUrl == null || gitUrl.isEmpty()) {
                gitUrl = gitRepositoryAction.getRepositoryURL();
            }

            if (gitDefaultBranch == null || gitDefaultBranch.isEmpty()) {
                gitDefaultBranch = gitRepositoryAction.getDefaultBranch();
            }

            if (branch == null || branch.isEmpty()) {
                this.branch = gitRepositoryAction.getBranch();
            }
        }
    }

    /**
     * Return if the Run is based on Git repository checking
     * the GIT_BRANCH environment variable or the user supplied
     * environment variables.
     * @param envVars
     * @return true if GIT_BRANCH is set or the user supplied GIT information via env vars.
     */
    private boolean isGit(EnvVars envVars) {
        if(envVars == null){
            return false;
        }

        return isUserSuppliedGit(envVars) || envVars.get(GIT_BRANCH) != null;
    }

    /**
     * Assembles a map of tags containing:
     * - Build Tags
     * - Global Job Tags set in Job Properties
     * - Global Tag set in Jenkins Global configuration
     *
     * @return a map containing all tags values
     */
    public Map<String, Set<String>> getTags() {
        Map<String, Set<String>> allTags = new HashMap<>();
        try {
            allTags = DatadogUtilities.getTagsFromGlobalTags();
        } catch(NullPointerException e){
            //noop
        }
        allTags = TagsUtil.merge(allTags, tags);
        allTags = TagsUtil.addTagToTags(allTags, "job", getJobName());

        if (nodeName != null) {
            allTags = TagsUtil.addTagToTags(allTags, "node", getNodeName("unknown"));
        }
        if (result != null) {
            allTags = TagsUtil.addTagToTags(allTags, "result", getResult("UNKNOWN"));
        }
        if (userId != null) {
            allTags = TagsUtil.addTagToTags(allTags, "user_id", getUserId());
        }
        if (jenkinsUrl != null) {
            allTags = TagsUtil.addTagToTags(allTags, "jenkins_url", getJenkinsUrl("unknown"));
        }
        if (branch != null) {
            allTags = TagsUtil.addTagToTags(allTags, "branch", getBranch("unknown"));
        }

        return allTags;
    }

    public Map<String, String> getTagsForTraces() {
        Map<String, Set<String>> allTags = new HashMap<>();
        try {
            allTags = DatadogUtilities.getTagsFromGlobalTags();
        } catch(NullPointerException e){
            //noop
        }
        allTags = TagsUtil.merge(allTags, tags);
        return TagsUtil.convertTagsToMapSingleValues(allTags);
    }

    public void setTags(Map<String, Set<String>> tags) {
        this.tags = tags;
    }

    private <A> A defaultIfNull(A value, A defaultValue) {
        if (value == null) {
            return defaultValue;
        } else {
            return value;
        }
    }

    @Nonnull
    public String getJobName() {
        return jobName;
    }

    @Nonnull
    public Map<String, String> getBuildConfigurations() {
        return buildConfigurations;
    }

    public String getResult(String value) {
        return defaultIfNull(result, value);
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public boolean isBuilding() {
        return isBuilding;
    }

    public String getHostname(String value) {
        return defaultIfNull(hostname, value);
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getBuildUrl(String value) {
        return defaultIfNull(buildUrl, value);
    }

    public Charset getCharset() {
        if (charsetName != null) {
            // Will throw an exception if there is an issue with
            // the charset canonical name.
            return Charset.forName(charsetName);
        }
        return Charset.defaultCharset();
    }

    public Map<String, String> getBuildParameters() {
        return this.buildParameters;
    }

    public String getNodeName(String value) {
        return defaultIfNull(nodeName, value);
    }

    public String getBranch(String value) {
        return defaultIfNull(branch, value);
    }

    public String getBuildNumber(String value) {
        return defaultIfNull(buildNumber, value);
    }

    public Long getDuration(Long value) {
        return defaultIfNull(duration, value);
    }

    public Long getEndTime(Long value) {
        return defaultIfNull(endTime, value);
    }

    public Long getStartTime(Long value) {
        return defaultIfNull(startTime, value);
    }

    public Long getMillisInQueue(Long value) {
        return defaultIfNull(millisInQueue, value);
    }

    public Long getPropagatedMillisInQueue(Long value) {
        return defaultIfNull(propagatedMillisInQueue, value);
    }

    public Integer getVersion() {
        return version;
    }

    public String getBuildTag(String value) {
        return defaultIfNull(buildTag, value);
    }

    @Nullable
    public String getUpstreamBuildTag(String value) {
        return defaultIfNull(upstreamBuildTag, value);
    }

    public String getJenkinsUrl(String value) {
        return defaultIfNull(jenkinsUrl, value);
    }

    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
    }

    public String getExecutorNumber(String value) {
        return defaultIfNull(executorNumber, value);
    }

    public String getWorkspace(String value) {
        return defaultIfNull(workspace, value);
    }

    public String getGitUrl(String value) {
        return defaultIfNull(gitUrl, value);
    }

    public String getGitCommit(String value) {
        return defaultIfNull(gitCommit, value);
    }

    public String getGitMessage(String value) {
        return defaultIfNull(gitMessage, value);
    }

    public String getGitAuthorName(final String value) {
        return defaultIfNull(gitAuthorName, value);
    }

    public String getGitAuthorEmail(final String value) {
        return defaultIfNull(gitAuthorEmail, value);
    }

    public String getGitCommitterName(final String value) {
        return defaultIfNull(gitCommitterName, value);
    }

    public String getGitCommitterEmail(final String value) {
        return defaultIfNull(gitCommitterEmail, value);
    }

    public String getGitAuthorDate(final String value) {
        return defaultIfNull(gitAuthorDate, value);
    }

    public String getGitCommitterDate(final String value) {
        return defaultIfNull(gitCommitterDate, value);
    }

    public String getGitDefaultBranch(String value) {
        return defaultIfNull(gitDefaultBranch, value);
    }

    public String getGitTag(String value) {
        return defaultIfNull(gitTag, value);
    }

    public String getUserId() {
        return userId;
    }

    private String getUserId(Run run) {
        if (promotedUserId != null){
            return promotedUserId;
        }
        String userName;
        List<CauseAction> actions = null;
        try {
            actions = run.getActions(CauseAction.class);
        }catch(NullPointerException e){
            //noop
        }
        if(actions != null){
            for (CauseAction action : actions) {
                if (action != null && action.getCauses() != null) {
                    for (Cause cause : action.getCauses()) {
                        userName = getUserId(cause);
                        if (userName != null) {
                            return userName;
                        }
                    }
                }
            }
        }

        if (run.getParent().getClass().getName().equals("hudson.maven.MavenModule")) {
            return "maven";
        }
        return "anonymous";
    }

    private String getUserId(Cause cause){
        if (cause instanceof TimerTrigger.TimerTriggerCause) {
            return "timer";
        } else if (cause instanceof SCMTrigger.SCMTriggerCause) {
            return "scm";
        } else if (cause instanceof Cause.UserIdCause) {
            String userName = ((Cause.UserIdCause) cause).getUserId();
            if (userName != null) {
                return userName;
            }
        } else if (cause instanceof Cause.UpstreamCause) {
            for (Cause upstreamCause : ((Cause.UpstreamCause) cause).getUpstreamCauses()) {
                String username = getUserId(upstreamCause);
                if (username != null) {
                    return username;
                }
            }
        }
        return null;
    }

    public String getUserEmail(final String value) {
        return defaultIfNull(this.userEmail, value);
    }

    private String getUserEmailByUserId(String userId) {
        try {
            if(StringUtils.isEmpty(userId)) {
                return null;
            }

            final User user = User.getById(userId, false);
            if(user == null){
                return null;
            }

            final Mailer.UserProperty mailInfo = user.getProperty(Mailer.UserProperty.class);
            if(mailInfo != null) {
                return mailInfo.getEmailAddress();
            }

            return null;
        } catch (Throwable ex) {
            DatadogUtilities.severe(LOGGER, ex, "Failed to obtain the user email associated with the user " + userId);
            return null;
        }
    }

    public String getUpstreamPipelineUrl() {
        return upstreamPipelineUrl;
    }

    public Long getUpstreamPipelineTraceId() {
        return upstreamPipelineTraceId;
    }

    public JSONObject addLogAttributes(){

        JSONObject payload = new JSONObject();

        try {

            JSONObject build = new JSONObject();
            build.put("number", this.buildNumber);
            build.put("id", this.buildId);
            build.put("url", this.buildUrl);
            payload.put("build", build);

            JSONObject http = new JSONObject();
            http.put("url", this.jenkinsUrl);
            payload.put("http", http);

            JSONObject jenkins = new JSONObject();
            jenkins.put("node_name", this.nodeName);
            jenkins.put("job_name", this.jobName);
            jenkins.put("build_tag", this.buildTag);
            jenkins.put("executor_number", this.executorNumber);
            jenkins.put("java_home", this.javaHome);
            jenkins.put("workspace", this.workspace);

            JSONObject promoted = new JSONObject();
            jenkins.put("promoted", promoted);
            if(promotedUrl != null){
                jenkins.put("url", this.promotedUrl);
            }
            if(promotedJobName != null){
                jenkins.put("job_name", this.promotedJobName);
            }
            if(promotedNumber != null){
                jenkins.put("number", this.promotedNumber);
            }
            if(promotedId != null){
                jenkins.put("id", this.promotedId);
            }
            if(promotedTimestamp != null){
                jenkins.put("timestamp", this.promotedTimestamp);
            }
            if(promotedUserName != null){
                jenkins.put("user_name", this.promotedUserName);
            }
            if(promotedUserId != null){
                jenkins.put("user_id", this.promotedUserId);
            }
            if(promotedJobFullName != null){
                jenkins.put("job_full_name", this.promotedJobFullName);
            }
            jenkins.put("result", this.result);

            payload.put("jenkins", jenkins);

            JSONObject scm = new JSONObject();
            scm.put("branch", this.branch);
            scm.put("git_url", this.gitUrl);
            scm.put("git_commit", this.gitCommit);
            payload.put("scm", scm);

            JSONObject user = new JSONObject();
            user.put("id", this.userId);
            payload.put("usr", user);

            payload.put("hostname", this.hostname);

            if(traceId != null){
                payload.put("dd.trace_id", this.traceId);
            }

            if(spanId != null) {
                payload.put("dd.span_id", this.spanId);
            }
            return payload;
        } catch (Exception e){
            DatadogUtilities.severe(LOGGER, e, "Failed to construct log attributes");
            return new JSONObject();
        }
    }
}
