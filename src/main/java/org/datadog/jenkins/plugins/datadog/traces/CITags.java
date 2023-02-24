package org.datadog.jenkins.plugins.datadog.traces;

public class CITags {

    public static final String LANGUAGE_TAG_KEY = "language";
    public static final String ERROR = "error";
    public static final String ERROR_MSG = "error.message";
    public static final String ERROR_TYPE = "error.type";
    public static final String ERROR_STACK = "error.stack";

    public static final String STATUS = "ci.status";
    public static final String CI_PARAMETERS = "ci.parameters";
    public static final String WORKSPACE_PATH = "ci.workspace_path";
    public static final String NODE_NAME = "ci.node.name";
    public static final String NODE_LABELS = "ci.node.labels";
    public static final String QUEUE_TIME = "ci.queue_time";
    public static final String _DD_HOSTNAME = "_dd.hostname";
    public static final String _DD_CI_INTERNAL = "_dd.ci.internal";
    public static final String _DD_CI_LEVEL = "_dd.ci.level";
    @Deprecated
    public static final String _DD_CI_BUILD_LEVEL = "_dd.ci.build_level"; //Maintain retrocompatibility
    public static final String _DD_CI_STAGES = "_dd.ci.stages";
    public static final String _DD_ORIGIN = "_dd.origin";

    public static final String _ID = ".id";
    public static final String _NAME = ".name";
    public static final String _NUMBER = ".number";
    public static final String _URL = ".url";
    @Deprecated
    public static final String _RESULT = ".result"; //Maintain retrocompatibility
    public static final String _CONFIGURATION = ".configuration";

    public static final String CI_PROVIDER_NAME = "ci.provider.name";
    public static final String USER_NAME = "user.name";
    public static final String USER_EMAIL = "user.email";


    public static final String GIT_REPOSITORY_URL = "git.repository_url";
    @Deprecated
    public static final String GIT_COMMIT__SHA = "git.commit_sha"; //Maintain retrocompatibility
    public static final String GIT_COMMIT_SHA = "git.commit.sha";
    public static final String GIT_COMMIT_MESSAGE = "git.commit.message";
    public static final String GIT_COMMIT_AUTHOR_NAME = "git.commit.author.name";
    public static final String GIT_COMMIT_AUTHOR_EMAIL = "git.commit.author.email";
    public static final String GIT_COMMIT_AUTHOR_DATE = "git.commit.author.date";
    public static final String GIT_COMMIT_COMMITTER_NAME = "git.commit.committer.name";
    public static final String GIT_COMMIT_COMMITTER_EMAIL = "git.commit.committer.email";
    public static final String GIT_COMMIT_COMMITTER_DATE = "git.commit.committer.date";
    public static final String GIT_DEFAULT_BRANCH = "git.default_branch";
    public static final String GIT_BRANCH = "git.branch";
    public static final String GIT_TAG = "git.tag";

    public static final String JENKINS_TAG = "jenkins.tag";
    public static final String JENKINS_EXECUTOR_NUMBER = "jenkins.executor.number";
    public static final String JENKINS_RESULT = "jenkins.result";

    public static final String STATUS_ERROR = "error";
    public static final String STATUS_UNSTABLE = "unstable";

    public static class Values {
        public static final String ORIGIN_CIAPP_PIPELINE = "ciapp-pipeline";
    }
}
