package org.datadog.jenkins.plugins.datadog.util.git;

public final class GitConstants {

    // User Supplied Git Environment Variables
    public static final String DD_GIT_REPOSITORY_URL = "DD_GIT_REPOSITORY_URL";
    public static final String DD_GIT_BRANCH = "DD_GIT_BRANCH";
    public static final String DD_GIT_TAG = "DD_GIT_TAG";
    public static final String DD_GIT_DEFAULT_BRANCH = "DD_GIT_DEFAULT_BRANCH";
    public static final String DD_GIT_COMMIT_SHA = "DD_GIT_COMMIT_SHA";
    public static final String DD_GIT_COMMIT_MESSAGE = "DD_GIT_COMMIT_MESSAGE";
    public static final String DD_GIT_COMMIT_AUTHOR_NAME = "DD_GIT_COMMIT_AUTHOR_NAME";
    public static final String DD_GIT_COMMIT_AUTHOR_EMAIL = "DD_GIT_COMMIT_AUTHOR_EMAIL";
    public static final String DD_GIT_COMMIT_AUTHOR_DATE = "DD_GIT_COMMIT_AUTHOR_DATE";
    public static final String DD_GIT_COMMIT_COMMITTER_NAME = "DD_GIT_COMMIT_COMMITTER_NAME";
    public static final String DD_GIT_COMMIT_COMMITTER_EMAIL = "DD_GIT_COMMIT_COMMITTER_EMAIL";
    public static final String DD_GIT_COMMIT_COMMITTER_DATE = "DD_GIT_COMMIT_COMMITTER_DATE";
    static final String[] USER_SUPPLIED_GIT_ENVVARS = {DD_GIT_REPOSITORY_URL,
            DD_GIT_BRANCH, DD_GIT_TAG, DD_GIT_COMMIT_SHA, DD_GIT_COMMIT_MESSAGE,
            DD_GIT_COMMIT_AUTHOR_NAME, DD_GIT_COMMIT_AUTHOR_EMAIL, DD_GIT_COMMIT_AUTHOR_DATE,
            DD_GIT_COMMIT_COMMITTER_NAME, DD_GIT_COMMIT_COMMITTER_EMAIL, DD_GIT_COMMIT_COMMITTER_DATE};

    // Jenkins Git Environment Variables
    public static final String GIT_REPOSITORY_URL = "GIT_URL";
    public static final String GIT_REPOSITORY_URL_ALT = "GIT_URL_1";
    public static final String GIT_BRANCH = "GIT_BRANCH";
    public static final String GIT_COMMIT = "GIT_COMMIT";

    private GitConstants(){}

}
