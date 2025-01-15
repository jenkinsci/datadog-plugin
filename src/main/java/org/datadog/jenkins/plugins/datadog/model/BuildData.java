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


import com.cloudbees.plugins.credentials.CredentialsParameterValue;
import hudson.EnvVars;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.BooleanParameterValue;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Computer;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
import org.datadog.jenkins.plugins.datadog.model.git.GitMetadata;
import org.datadog.jenkins.plugins.datadog.traces.BuildConfigurationParser;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

public class BuildData implements Serializable {

    private static final BuildData EMPTY;

    static {
      try {
          // exceptions below should never happen
          // since constructor exits immediately if run is null
          EMPTY = new BuildData(null, null);
      } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
      } catch (IOException e) {
          throw new RuntimeException(e);
      }
    }

    private static final ThreadLocal<Set<Run<?, ?>>> BUILD_DATA_BEING_CREATED = ThreadLocal.withInitial(BuildData::identityHashSet);

    private static <T> Set<T> identityHashSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * This is a workaround for a BuildData initialization issue.
     * <p>
     * Some of the fields in this class are initialized with the values obtained from a Run instance.
     * Querying Run fields can trigger run initialization in some cases (e.g. loading run data from disk when resuming a run after Jenkins restart).
     * During run initialization some logs are written.
     * Writing logs, in turn, requires creating a TaskListener associated with the run.
     * Creating the listener triggers initialization of DatadogTaskListenerDecorator, as the listener's logger needs to be decorated.
     * The decorator creates another BuildData instance, whose creation starts the whole cycle from the beginning.
     * As the result, the code enters an endless cycle of initializing BuildData while initializing BuildData, which terminates with a stack overflow.
     * <p>
     * The code below checks that BuildData for the provided run is already being created in the current thread.
     * If that is the case, empty BuildData instance is returned for the nested calls in order to break the cycle.
     * As a side effect, whatever logs are written while Run instance is being initialized will not be tagged with that run's data.
     */
    // TODO split BuildData fields that are used by DatadogWriter into a separate class whose initialization will not trigger run data load
    @Nonnull
    public static BuildData create(@Nullable Run<?, ?> run, @Nullable TaskListener listener) {
        if (!BUILD_DATA_BEING_CREATED.get().add(run)) {
            String runName = run != null ? run.getDisplayName() : null;
            DatadogUtilities.severe(LOGGER, null, "BuildData creation is in progress for run " + runName + "; using empty data");
            // there is another call up the stack that is creating BuildData for the same Run,
            // so the initial caller will get fully populated data
            // (empty data will only be used by the nested calls triggered by the original BuildData init)
            return BuildData.EMPTY;
        }
        try {
            return new BuildData(run, listener);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(LOGGER, e, "Interrupted while creating build data");
            return BuildData.EMPTY;

        } catch (Exception e) {
            DatadogUtilities.severe(LOGGER, e, "Error creating build data");
            return BuildData.EMPTY;

        } finally {
            BUILD_DATA_BEING_CREATED.get().remove(run);
        }
    }

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
    private boolean isWorkflowRun;
    private String hostname;
    private String userId;
    private String userEmail;
    private Map<String, Set<String>> tags;

    private Long startTime;
    private Long endTime;
    private Long duration;

    private GitMetadata gitMetadata = GitMetadata.EMPTY;
    private GitMetadata pipelineDefinitionGitMetadata = GitMetadata.EMPTY;

    /**
     * Queue time that is reported by the Jenkins Queue API.
     * This is the time that the build was sitting in queue BEFORE starting to execute:
     * <ul>
     *  <li>if it was waiting for another build to finish because of the parallel execution settings</li>
     *  <li>if it was waiting for a "quiet period" to finish because of the throttle settings</li>
     *  <li>if it was waiting for an executor to become available (this point only applies to Freestyle builds; a pipeline waiting for executor will report {@link #propagatedMillisInQueue}</li>
     * </ul>
     * <p>
     * This queue time goes BEFORE timestamp that is reported by {@link Run#getStartTimeInMillis()}
     * and is NOT included into the duration reported by {@link Run#getDuration()}.
     */
    private long millisInQueue;

    /**
     * Queue time that is propagated from a child node of a pipeline:
     * if a pipeline is configured to execute in a specific executor (with the top-level `agent {}` section),
     * it will be reported as started even if that executor is not available.
     * In this case the time spent waiting for the executor will be reported by the first child node of the pipeline,
     * and propagated to the pipeline from there.
     * <p>
     * This queue time goes AFTER timestamp that is reported by {@link Run#getStartTimeInMillis()}
     * and is INCLUDED into the duration reported by {@link Run#getDuration()}.
     */
    private long propagatedMillisInQueue;

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
    private Long traceId;
    private Long spanId;

    @Nullable
    private String upstreamPipelineUrl;
    @Nullable
    private Long upstreamPipelineTraceId;

    private BuildData(Run<?, ?> run, @Nullable TaskListener listener) throws IOException, InterruptedException {
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

            TraceSpan.TraceSpanContext buildSpanContext = buildSpanAction.getBuildSpanContext();
            this.traceId = buildSpanContext.getTraceId();
            this.spanId = buildSpanContext.getSpanId();
        }

        // Populate instance using environment variables.
        populateEnvVariables(envVars);

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

        this.isWorkflowRun = run instanceof WorkflowRun;

        PipelineQueueInfoAction action = run.getAction(PipelineQueueInfoAction.class);
        if (action != null) {
            this.millisInQueue = action.getQueueTimeMillis();
            this.propagatedMillisInQueue = action.getPropagatedQueueTimeMillis();
        }

        long runStartTime = run.getStartTimeInMillis();
        if (runStartTime != 0) {
            // advance start time: queue time should not be considered as part of the build duration
            this.startTime = runStartTime + this.propagatedMillisInQueue;
        }

        long runDuration = run.getDuration();
        if (runDuration != 0) {
            // adjust duration: queue time should not be considered as part of the build duration
            this.duration = runDuration - this.propagatedMillisInQueue;
        } else if (this.startTime != null) {
            this.duration = System.currentTimeMillis() - this.startTime;
        } else {
            this.duration = 0L;
        }

        if (this.startTime != null) {
            // we set end time regardless of current pipeline status, but for in-progress pipelines its value will be ignored
            this.endTime = this.startTime + this.duration;
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
        } else if (run instanceof AbstractBuild) {
            AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
            Node node = build.getBuiltOn();
            Computer computer = node != null ? node.toComputer() : null;
            this.hostname = DatadogUtilities.getNodeHostname(envVars, computer);
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

        if (pipelineInfo != null && pipelineInfo.getExecutorNumber() != null){
            this.executorNumber = pipelineInfo.getExecutorNumber();
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

        populateUpstreamPipelineData(run);

        this.gitMetadata = getGitMetadata(run, envVars);
        this.pipelineDefinitionGitMetadata = getPipelineDefinitionGitMetadata(run);
    }

    private static GitMetadata getGitMetadata(Run<?, ?> run, EnvVars environment) {
        GitMetadata gitMetadata = GitMetadata.EMPTY;

        GitMetadataAction gitMetadataAction = run.getAction(GitMetadataAction.class);
        if (gitMetadataAction != null) {
            gitMetadata = GitMetadata.merge(gitMetadata, gitMetadataAction.getMetadata());
        }

        gitMetadata = GitMetadata.merge(gitMetadata, GitUtils.buildGitMetadataWithJenkinsEnvVars(environment));
        gitMetadata = GitMetadata.merge(gitMetadata, GitUtils.buildGitMetadataWithUserSuppliedEnvVars(environment));
        return gitMetadata;
    }

    private static GitMetadata getPipelineDefinitionGitMetadata(Run<?, ?> run) {
        GitMetadataAction gitMetadataAction = run.getAction(GitMetadataAction.class);
        if (gitMetadataAction != null) {
            return gitMetadataAction.getPipelineDefinitionMetadata();
        } else {
            return GitMetadata.EMPTY;
        }
    }

    private void populateUpstreamPipelineData(Run<?, ?> run) {
        CauseAction causeAction = run.getAction(CauseAction.class);
        if (causeAction == null) {
            return;
        }
        Cause.UpstreamCause upstreamCause = causeAction.findCause(Cause.UpstreamCause.class);
        if (upstreamCause == null) {
            return;
        }

        String upstreamUrl = upstreamCause.getUpstreamUrl();
        int upstreamBuild = upstreamCause.getUpstreamBuild();
        if (this.jenkinsUrl != null && upstreamUrl != null) {
            this.upstreamPipelineUrl = jenkinsUrl + upstreamUrl + upstreamBuild + "/";
        }

        String upstreamProject = upstreamCause.getUpstreamProject();
        if (upstreamProject != null) {
            this.upstreamBuildTag = "jenkins-" + upstreamProject.replace('/', '-') + "-" + upstreamBuild;

            BuildSpanAction buildSpanAction = run.getAction(BuildSpanAction.class);
            if (buildSpanAction != null) {
                TraceSpan.TraceSpanContext upstreamSpanContext = buildSpanAction.getUpstreamSpanContext();
                if (upstreamSpanContext != null) {
                    this.upstreamPipelineTraceId = upstreamSpanContext.getTraceId();
                }
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

    private void populateEnvVariables(EnvVars envVars) {
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

        String executorNumber = envVars.get("EXECUTOR_NUMBER");
        if (StringUtils.isNotBlank(executorNumber)){
            this.executorNumber = executorNumber;
        }
        this.javaHome = envVars.get("JAVA_HOME");

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
     * Assembles a map of tags containing:
     * - Build Tags
     * - Global Job Tags set in Job Properties
     * - Global Tag set in Jenkins Global configuration
     *
     * @return a map containing all tags values
     */
    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
    public Map<String, Set<String>> getTags() {
        Map<String, Set<String>> allTags = new HashMap<>();
        try {
            allTags = DatadogUtilities.getTagsFromGlobalTags();
        } catch (NullPointerException e){
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
        if (gitMetadata.getBranch() != null) {
            allTags = TagsUtil.addTagToTags(allTags, "branch", getBranch("unknown"));
        }

        return allTags;
    }

    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
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
        return defaultIfNull(gitMetadata.getBranch(), value);
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

    /**
     * Returns the total time this build spent waiting in queue.
     */
    public long getTotalQueueTimeMillis() {
        // Need to sum both queue times because they are not mutually exclusive.
        // In the case of pipelines millisInQueue will include the time spent waiting for other builds to finish (before pipeline start),
        // and propagatedMillisInQueue will include the time spent waiting for an executor (after pipeline start).
        return Math.max(millisInQueue, 0) + Math.max(propagatedMillisInQueue, 0);
    }

    public Integer getVersion() {
        return version;
    }

    public Long getTraceId() {
        return traceId;
    }

    public Long getSpanId() {
        return spanId;
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
        return defaultIfNull(gitMetadata.getRepositoryURL(), value);
    }

    public String getGitCommit(String value) {
        return defaultIfNull(gitMetadata.getCommitMetadata().getCommit(), value);
    }

    public String getGitMessage(String value) {
        return defaultIfNull(gitMetadata.getCommitMetadata().getMessage(), value);
    }

    public String getGitAuthorName(final String value) {
        return defaultIfNull(gitMetadata.getCommitMetadata().getAuthorName(), value);
    }

    public String getGitAuthorEmail(final String value) {
        return defaultIfNull(gitMetadata.getCommitMetadata().getAuthorEmail(), value);
    }

    public String getGitCommitterName(final String value) {
        return defaultIfNull(gitMetadata.getCommitMetadata().getCommitterName(), value);
    }

    public String getGitCommitterEmail(final String value) {
        return defaultIfNull(gitMetadata.getCommitMetadata().getCommitterEmail(), value);
    }

    public String getGitAuthorDate(final String value) {
        return defaultIfNull(gitMetadata.getCommitMetadata().getAuthorDate(), value);
    }

    public String getGitCommitterDate(final String value) {
        return defaultIfNull(gitMetadata.getCommitMetadata().getCommitterDate(), value);
    }

    public String getGitDefaultBranch(String value) {
        return defaultIfNull(gitMetadata.getDefaultBranch(), value);
    }

    public String getGitTag(String value) {
        return defaultIfNull(gitMetadata.getCommitMetadata().getTag(), value);
    }

    public GitMetadata getGitMetadata() {
        return gitMetadata;
    }

    /**
     * For multi-branch pipelines it is possible to check out pipeline definition from one repository,
     * then, when executing the pipeline script, do another Git checkout from a different repository.
     * This method returns Git metadata for original pipeline script checkout
     */
    public GitMetadata getPipelineDefinitionGitMetadata() {
        return pipelineDefinitionGitMetadata;
    }

    public String getUserId() {
        return userId;
    }

    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
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

    @Nullable
    public String getUpstreamPipelineUrl() {
        return upstreamPipelineUrl;
    }

    @Nullable
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
            scm.put("branch", getBranch(null));
            scm.put("git_url", getGitUrl(null));
            scm.put("git_commit", getGitCommit(null));
            payload.put("scm", scm);

            JSONObject user = new JSONObject();
            user.put("id", this.userId);
            payload.put("usr", user);

            payload.put("hostname", this.hostname);

            if(traceId != null){
                payload.put("dd.trace_id", Long.toUnsignedString(this.traceId));
            }

            if(spanId != null) {
                payload.put("dd.span_id",  Long.toUnsignedString(this.spanId));
            }
            return payload;
        } catch (Exception e){
            DatadogUtilities.severe(LOGGER, e, "Failed to construct log attributes");
            return new JSONObject();
        }
    }

    /**
     * The accurate start time for a pipeline (WorkflowRun) is only known once the first step starts executing
     * (everything before that counts as queued time: see the definition of {@link #propagatedMillisInQueue})
     */
    public boolean isStartTimeKnown() {
        return !this.isBuilding || !this.isWorkflowRun || this.propagatedMillisInQueue >= 0;
    }
}
