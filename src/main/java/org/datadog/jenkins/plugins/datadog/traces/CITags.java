package org.datadog.jenkins.plugins.datadog.traces;

public class CITags {

    public static final String WORKSPACE_PATH = "ci.workspace_path";
    public static final String NODE_NAME = "ci.node.name";
    public static final String _DD_HOSTNAME = "_dd.hostname";
    public static final String _DD_CI_INTERNAL = "_dd.ci.internal";

    public static final String _ID = ".id";
    public static final String _NAME = ".name";
    public static final String _NUMBER = ".number";
    public static final String _URL = ".url";
    public static final String _RESULT = ".result";
    public static final String _CONFIGURATION = ".configuration";

    public static final String CI_PROVIDER_NAME = "ci.provider.name";
    public static final String USER_NAME = "user.name";


    public static final String GIT_REPOSITORY_URL = "git.repository_url";
    public static final String GIT_COMMIT_SHA = "git.commit_sha";
    public static final String GIT_BRANCH = "git.branch";
    public static final String GIT_TAG = "git.tag";

    public static final String JENKINS_TAG = "jenkins.tag";
    public static final String JENKINS_EXECUTOR_NUMBER = "jenkins.executor.number";
    public static final String JENKINS_RESULT = "jenkins.result";

    public static final String ERROR = "error";
}
