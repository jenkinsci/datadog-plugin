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

import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidRepositoryURL;

import com.cloudbees.plugins.credentials.CredentialsParameterValue;
import hudson.EnvVars;
import hudson.model.BooleanParameterValue;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.TextParameterValue;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.util.LogTaskListener;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanManager;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private String buildTag;
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
    private String hostname;
    private String userId;
    private Map<String, Set<String>> tags;

    private Long startTime;
    private Long endTime;
    private Long duration;
    private Long secondsInQueue;
    private Long propagatedSecondsInQueue;
    private Long millisInQueue;
    private Long propagatedMillisInQueue;

    private String traceId;
    private String spanId;

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public BuildData(Run run, TaskListener listener) throws IOException, InterruptedException {
        if (run == null) {
            return;
        }
        EnvVars envVars;
        if(listener != null){
            envVars = run.getEnvironment(listener);
        }else{
            envVars = run.getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
        }

        setTags(DatadogUtilities.getBuildTags(run, envVars));

        // Populate instance using environment variables.
        populateEnvVariables(envVars);

        // Populate instance using Git info if possible.
        // Set all Git commit related variables.
        if(isGit(envVars)){
            populateGitVariables(run, listener, envVars);
        }

        // Populate instance using run instance
        // Set StartTime, EndTime and Duration
        long startTimeInMs = run.getStartTimeInMillis();
        setStartTime(startTimeInMs);
        long durationInMs = run.getDuration();
        if (durationInMs == 0 && startTimeInMs != 0) {
            durationInMs = System.currentTimeMillis() - startTimeInMs;
        }
        setDuration(durationInMs);
        if (durationInMs != 0 && startTimeInMs != 0) {
            Long endTimeInMs = startTimeInMs + durationInMs;
            setEndTime(endTimeInMs);
        }

        // Set Jenkins Url
        setJenkinsUrl(DatadogUtilities.getJenkinsUrl());
        // Set UserId
        setUserId(getUserId(run));
        // Set Result and completed status
        setResult(run.getResult() == null ? null : run.getResult().toString());
        setCompleted(run.getResult() != null && run.getResult().completeBuild);

        // Set Build Number
        setBuildNumber(String.valueOf(run.getNumber()));
        // Set Hostname
        setHostname(DatadogUtilities.getHostname(envVars));
        // Save charset canonical name
        setCharset(run.getCharset());

        // Set Job Name
        String jobName = null;
        try {
            jobName = run.getParent().getFullName();
        } catch(NullPointerException e){
            //noop
        }
        setJobName(jobName == null ? null : jobName.replaceAll("»", "/").replaceAll(" ", ""));
        // Set Jenkins Url
        String jenkinsUrl = DatadogUtilities.getJenkinsUrl();
        if("unknown".equals(jenkinsUrl) && envVars != null && envVars.get("JENKINS_URL") != null
                && !envVars.get("JENKINS_URL").isEmpty()) {
            jenkinsUrl = envVars.get("JENKINS_URL");
        }
        setJenkinsUrl(jenkinsUrl);

        // Build parameters
        populateBuildParameters(run);

        // Set Tracing IDs
        final TraceSpan buildSpan = BuildSpanManager.get().get(getBuildTag(""));
        if(buildSpan !=null) {
            setTraceId(Long.toUnsignedString(buildSpan.context().getTraceId()));
            setSpanId(Long.toUnsignedString(buildSpan.context().getSpanId()));
        }
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
        setBuildId(envVars.get("BUILD_ID"));
        setBuildUrl(envVars.get("BUILD_URL"));
        setNodeName(envVars.get("NODE_NAME"));
        setBuildTag(envVars.get("BUILD_TAG"));
        setExecutorNumber(envVars.get("EXECUTOR_NUMBER"));
        setJavaHome(envVars.get("JAVA_HOME"));
        setWorkspace(envVars.get("WORKSPACE"));
        if (envVars.get("GIT_BRANCH") != null) {
            setBranch(envVars.get("GIT_BRANCH"));
            setGitUrl(DatadogUtilities.getGitRepositoryUrl(envVars));
            setGitCommit(envVars.get("GIT_COMMIT"));
        } else if (envVars.get("CVS_BRANCH") != null) {
            setBranch(envVars.get("CVS_BRANCH"));
        }
        setPromotedUrl(envVars.get("PROMOTED_URL"));
        setPromotedJobName(envVars.get("PROMOTED_JOB_NAME"));
        setPromotedNumber(envVars.get("PROMOTED_NUMBER"));
        setPromotedId(envVars.get("PROMOTED_ID"));
        setPromotedTimestamp(envVars.get("PROMOTED_TIMESTAMP"));
        setPromotedUserName(envVars.get("PROMOTED_USER_NAME"));
        setPromotedUserId(envVars.get("PROMOTED_USER_ID"));
        setPromotedJobFullName(envVars.get("PROMOTED_JOB_FULL_NAME"));
    }


    /**
     * Populate git commit related information in the BuildData instance.
     * @param run
     * @param listener
     * @param envVars
     */
    private void populateGitVariables(Run<?,?> run, TaskListener listener, EnvVars envVars) {
        // First we obtain the actions to check if the Git information was already calculated.
        // If so, we want to use this information to avoid creating a new Git client instance
        // to calculate the same information.
        GitCommitAction gitCommitAction = run.getAction(GitCommitAction.class);
        GitRepositoryAction gitRepositoryAction = run.getAction(GitRepositoryAction.class);

        boolean repoInfoAlreadyCalculated = gitRepositoryAction != null &&
                gitRepositoryAction.getRepositoryURL() != null &&
                gitRepositoryAction.getRepositoryURL().equals(this.gitUrl);

        if(repoInfoAlreadyCalculated){
            this.gitDefaultBranch = gitRepositoryAction.getDefaultBranch();
        }

        boolean commitInfoAlreadyCalculated = gitCommitAction != null &&
                gitCommitAction.getCommit() != null &&
                gitCommitAction.getCommit().equals(this.gitCommit);

        if(commitInfoAlreadyCalculated){
            this.gitMessage = gitCommitAction.getMessage();
            this.gitAuthorName = gitCommitAction.getAuthorName();
            this.gitAuthorEmail = gitCommitAction.getAuthorEmail();
            this.gitAuthorDate = gitCommitAction.getAuthorDate();
            this.gitCommitterName = gitCommitAction.getCommitterName();
            this.gitCommitterEmail = gitCommitAction.getCommitterEmail();
            this.gitCommitterDate = gitCommitAction.getCommitterDate();
        }

        // If all Git info was already calculated, we finish the method here.
        if(repoInfoAlreadyCalculated && commitInfoAlreadyCalculated) {
            return;
        }

        // At this point, there is some Git information that we need to calculate.
        // We use the same Git client instance to calculate all git information
        // because creating a Git client is a very expensive operation.
        GitClient gitClient = null;
        if(isValidCommit(gitCommit) || isValidRepositoryURL(this.gitUrl)) {
            // Create a new Git client is a very expensive operation.
            // Avoid creating Git clients as much as possible.
            gitClient = GitUtils.newGitClient(run, listener, envVars, this.nodeName, this.workspace);
        }

        if(isValidCommit(this.gitCommit)){
            gitCommitAction = GitUtils.buildGitCommitAction(run, gitClient ,this.gitCommit);
            if(gitCommitAction != null) {
                this.gitMessage = gitCommitAction.getMessage();
                this.gitAuthorName = gitCommitAction.getAuthorName();
                this.gitAuthorEmail = gitCommitAction.getAuthorEmail();
                this.gitAuthorDate = gitCommitAction.getAuthorDate();
                this.gitCommitterName = gitCommitAction.getCommitterName();
                this.gitCommitterEmail = gitCommitAction.getCommitterEmail();
                this.gitCommitterDate = gitCommitAction.getCommitterDate();
            }
        }

        if(isValidRepositoryURL(this.gitUrl)){
            gitRepositoryAction = GitUtils.buildGitRepositoryAction(run, gitClient, envVars, this.gitUrl);
            if(gitRepositoryAction != null) {
                this.gitDefaultBranch = gitRepositoryAction.getDefaultBranch();
            }
        }
    }

    /**
     * Return if the Run is based on Git repository checking
     * the GIT_BRANCH environment variable.
     * @param envVars
     * @return true if GIT_BRANCH is set.
     */
    private boolean isGit(EnvVars envVars) {
        if(envVars == null){
            return false;
        }
        return envVars.get("GIT_BRANCH") != null;
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
        allTags = TagsUtil.addTagToTags(allTags, "job", getJobName("unknown"));

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

    public String getJobName(String value) {
        return defaultIfNull(jobName, value);
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getResult(String value) {
        return defaultIfNull(result, value);
    }

    public void setResult(String result) {
        this.result = result;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        this.isCompleted = completed;
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

    public void setBuildUrl(String buildUrl) {
        this.buildUrl = buildUrl;
    }

    public Charset getCharset() {
        if (charsetName != null) {
            // Will throw an exception if there is an issue with
            // the charset canonical name.
            return Charset.forName(charsetName);
        }
        return Charset.defaultCharset();
    }

    public void setCharset(Charset charset) {
        if (charset != null) {
            this.charsetName = charset.name();
        }
    }

    public Map<String, String> getBuildParameters() {
        return this.buildParameters;
    }

    public String getNodeName(String value) {
        return defaultIfNull(nodeName, value);
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getBranch(String value) {
        return defaultIfNull(branch, value);
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getBuildNumber(String value) {
        return defaultIfNull(buildNumber, value);
    }

    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public Long getDuration(Long value) {
        return defaultIfNull(duration, value);
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public Long getEndTime(Long value) {
        return defaultIfNull(endTime, value);
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Long getStartTime(Long value) {
        return defaultIfNull(startTime, value);
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getMillisInQueue(Long value) {
        return defaultIfNull(millisInQueue, value);
    }

    public void setMillisInQueue(Long millisInQueue) {
        this.millisInQueue = millisInQueue;
    }

    public Long getPropagatedMillisInQueue(Long value) {
        return defaultIfNull(propagatedMillisInQueue, value);
    }

    public void setPropagatedMillisInQueue(Long propagatedMillisInQueue) {
        this.propagatedMillisInQueue = propagatedMillisInQueue;
    }

    public String getBuildId(String value) {
        return defaultIfNull(buildId, value);
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

    public String getBuildTag(String value) {
        return defaultIfNull(buildTag, value);
    }

    public void setBuildTag(String buildTag) {
        this.buildTag = buildTag;
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

    public void setExecutorNumber(String executorNumber) {
        this.executorNumber = executorNumber;
    }

    public String getJavaHome(String value) {
        return defaultIfNull(javaHome, value);
    }

    public void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }

    public String getWorkspace(String value) {
        return defaultIfNull(workspace, value);
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public String getGitUrl(String value) {
        return defaultIfNull(gitUrl, value);
    }

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public String getGitCommit(String value) {
        return defaultIfNull(gitCommit, value);
    }

    public void setGitCommit(String gitCommit) {
        this.gitCommit = gitCommit;
    }

    public String getGitMessage(String value) {
        return defaultIfNull(gitMessage, value);
    }

    public void setGitMessage(String gitMessage) {
        this.gitMessage = gitMessage;
    }

    public String getGitAuthorName(final String value) {
        return defaultIfNull(gitAuthorName, value);
    }

    public void setGitAuthorName(String gitAuthorName) {
        this.gitAuthorName = gitAuthorName;
    }

    public String getGitAuthorEmail(final String value) {
        return defaultIfNull(gitAuthorEmail, value);
    }

    public void setGitAuthorEmail(String gitAuthorEmail) {
        this.gitAuthorEmail = gitAuthorEmail;
    }

    public String getGitCommitterName(final String value) {
        return defaultIfNull(gitCommitterName, value);
    }

    public void setGitCommitterName(String gitCommitterName) {
        this.gitCommitterName = gitCommitterName;
    }

    public String getGitCommitterEmail(final String value) {
        return defaultIfNull(gitCommitterEmail, value);
    }

    public void setGitCommitterEmail(String gitCommitterEmail) {
        this.gitCommitterEmail = gitCommitterEmail;
    }

    public String getGitAuthorDate(final String value) {
        return defaultIfNull(gitAuthorDate, value);
    }

    public void setGitAuthorDate(String gitAuthorDate) {
        this.gitAuthorDate = gitAuthorDate;
    }

    public String getGitCommitterDate(final String value) {
        return defaultIfNull(gitCommitterDate, value);
    }

    public void setGitCommitterDate(String gitCommitterDate) {
        this.gitCommitterDate = gitCommitterDate;
    }

    public String getGitDefaultBranch(String value) {
        return defaultIfNull(gitDefaultBranch, value);
    }

    public void setGitDefaultBranch(String gitDefaultBranch) {
        this.gitDefaultBranch = gitDefaultBranch;
    }

    public String getPromotedUrl(String value) {
        return defaultIfNull(promotedUrl, value);
    }

    public void setPromotedUrl(String promotedUrl) {
        this.promotedUrl = promotedUrl;
    }

    public String getPromotedJobName(String value) {
        return defaultIfNull(promotedJobName, value);
    }

    public void setPromotedJobName(String promotedJobName) {
        this.promotedJobName = promotedJobName;
    }

    public String getPromotedNumber(String value) {
        return defaultIfNull(promotedNumber, value);
    }

    public void setPromotedNumber(String promotedNumber) {
        this.promotedNumber = promotedNumber;
    }

    public String getPromotedId(String value) {
        return defaultIfNull(promotedId, value);
    }

    public void setPromotedId(String promotedId) {
        this.promotedId = promotedId;
    }

    public String getPromotedTimestamp(String value) {
        return defaultIfNull(promotedTimestamp, value);
    }

    public void setPromotedTimestamp(String promotedTimestamp) {
        this.promotedTimestamp = promotedTimestamp;
    }

    public String getPromotedUserName(String value) {
        return defaultIfNull(promotedUserName, value);
    }

    public void setPromotedUserName(String promotedUserName) {
        this.promotedUserName = promotedUserName;
    }

    public String getPromotedUserId(String value) {
        return defaultIfNull(promotedUserId, value);
    }

    public void setPromotedUserId(String promotedUserId) {
        this.promotedUserId = promotedUserId;
    }

    public String getPromotedJobFullName(String value) {
        return defaultIfNull(promotedJobFullName, value);
    }

    public void setPromotedJobFullName(String promotedJobFullName) {
        this.promotedJobFullName = promotedJobFullName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public JSONObject addLogAttributes(JSONObject payload){
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
    }

}
