package org.datadog.jenkins.plugins.datadog.util.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.audit.DatadogAudit;
import org.datadog.jenkins.plugins.datadog.model.git.GitCommitMetadata;
import org.datadog.jenkins.plugins.datadog.model.git.GitMetadata;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

public final class GitUtils {

    private static final String EXAMINE_REPO_REFS_ENV_VAR = "DD_JENKINS_EXAMINE_REPO_REFS";

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

    // Jenkins Git Environment Variables
    public static final String GIT_REPOSITORY_URL = "GIT_URL";
    public static final String GIT_REPOSITORY_URL_ALT = "GIT_URL_1";
    public static final String GIT_BRANCH = "GIT_BRANCH";
    public static final String GIT_BRANCH_ALT = "BRANCH_NAME";
    public static final String GIT_COMMIT = "GIT_COMMIT";
    /**
     * For a multibranch project corresponding to some kind of change request,
     * this will be set to the name of the actual head on the source control system which may or may not be different from BRANCH_NAME.
     * For example in GitHub or Bitbucket this would have the name of the origin branch whereas BRANCH_NAME would be something like PR-24
     */
    public static final String CHANGE_BRANCH = "CHANGE_BRANCH";

    private static final Logger LOGGER = Logger.getLogger(GitUtils.class.getName());
    private static final Pattern SCP_REPO_URI_REGEX = Pattern.compile("^([\\w.~-]+@)?(?<host>[\\w.-]+):(?<path>[\\w./-]+)(?:\\?|$)(.*)$");

    private GitUtils() {
    }

    /**
     * Check if the git commit is a valid commit.
     *
     * @param gitCommit the git commit to evaluate
     * @return true if the git commit is a valid SHA 40 (HEX)
     */
    public static boolean isValidCommitSha(String gitCommit) {
        if (gitCommit == null || gitCommit.length() != 40) {
            return false;
        }
        for (int i = 0; i < gitCommit.length(); i++) {
            char c = gitCommit.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the git repository URL is a valid repository
     *
     * @param gitRepositoryURL the current git repository
     * @return true if the git repository url is a valid repository in either http or scp form.
     */
    public static boolean isValidRepositoryURL(String gitRepositoryURL) {
        if (gitRepositoryURL == null || gitRepositoryURL.isEmpty()) {
            return false;
        }

        try {
            final URI uri = new URI(gitRepositoryURL);
            return uri.getHost() != null;
        } catch (URISyntaxException e) {
            return SCP_REPO_URI_REGEX.matcher(gitRepositoryURL).matches();
        }
    }

    public static GitMetadata buildGitMetadata(@Nullable final GitClient gitClient, @Nullable String branchHint) {
        try {
            if (gitClient == null) {
                LOGGER.fine("Unable to build Git metadata. GitClient is null");
                return null;

            } else {
                long repoCheckStart = System.currentTimeMillis();
                boolean examineRepoRefs = DatadogUtilities.envVar(EXAMINE_REPO_REFS_ENV_VAR, true);
                GitMetadataBuilderCallback.Result result = gitClient.withRepository(new GitMetadataBuilderCallback(branchHint, examineRepoRefs));
                LOGGER.fine("Examined Git repository in " + (System.currentTimeMillis() - repoCheckStart) + " ms");

                GitMetadata.Builder builder = new GitMetadata.Builder();
                builder.repositoryURL(result.repoUrl);
                builder.defaultBranch(normalizeBranch(result.defaultBranch));
                builder.branch(result.branch);
                builder.commitMetadata(buildCommitMetadata(gitClient, result.branch));
                return builder.build();
            }

        } catch (Exception e) {
            DatadogUtilities.logException(LOGGER, Level.FINE, "Unable to build git metadata", e);
            return null;
        }
    }

    private static GitCommitMetadata buildCommitMetadata(@Nullable final GitClient gitClient, String branch) {
        try {
            GitCommitMetadata.Builder builder = new GitCommitMetadata.Builder();

            final RevCommit revCommit = searchRevCommit(gitClient, "HEAD");
            if (revCommit == null) {
                LOGGER.fine("Unable to get git commit information. RevCommit is null");
                return null;
            }

            builder.tag(normalizeTag(branch)); // it is possible that branch ref contains a tag
            builder.commit(revCommit.getName());

            String message;
            try {
                message = StringUtils.abbreviate(revCommit.getFullMessage(), 1500);
            } catch (Exception e) {
                DatadogUtilities.logException(LOGGER, Level.FINE, "Unable to obtain git commit full message. Selecting short message", e);
                message = revCommit.getShortMessage();
            }
            builder.message(message);

            final PersonIdent authorIdent = revCommit.getAuthorIdent();
            if (authorIdent != null) {
                builder.authorName(authorIdent.getName());
                builder.authorEmail(authorIdent.getEmailAddress());
                builder.authorDate(DatadogUtilities.toISO8601(authorIdent.getWhen()));
            }

            final PersonIdent committerIdent = revCommit.getCommitterIdent();
            if (committerIdent != null) {
                builder.committerName(committerIdent.getName());
                builder.committerEmail(committerIdent.getEmailAddress());
                builder.committerDate(DatadogUtilities.toISO8601(committerIdent.getWhen()));
            }

            return builder.build();

        } catch (Exception e) {
            DatadogUtilities.logException(LOGGER, Level.FINE, "Unable to get git commit information", e);
            return null;
        }
    }

    /**
     * Return the RevCommit for a certain commit based on the information
     * stored in a certain workspace of a certain node.
     *
     * @param gitCommit the Git commit SHA to search info.
     * @param gitClient the Git client used.
     * @return revCommit
     */
    private static RevCommit searchRevCommit(@Nullable final GitClient gitClient, final String gitCommit) {
        try {
            if (gitClient == null) {
                LOGGER.fine("Unable to search RevCommit. GitClient is null");
                return null;
            }

            return gitClient.withRepository(new RevCommitRepositoryCallback(gitCommit));
        } catch (Exception e) {
            LOGGER.fine("Unable to search RevCommit. Error: " + e);
            return null;
        }
    }

    /**
     * Creates a new instance of a {@code GitClient}.
     *
     * @param listener  the task listener
     * @param envVars   the env vars available
     * @param workspace the workspace to use to build the Git client
     * @return gitClient
     */
    @Nullable
    public static GitClient newGitClient(final TaskListener listener, final EnvVars envVars, final FilePath workspace) {
        long start = System.currentTimeMillis();
        try {
            try {
                if (workspace == null) {
                    return null;
                }

                final Git git = Git.with(listener, envVars).in(workspace);
                return git.getClient();
            } catch (Exception e) {
                LOGGER.fine("Unable to create GitClient. Error: " + e);
                return null;
            }
        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("GitUtils.newGitClient", start, end);
        }
    }

    public static GitMetadata buildGitMetadataWithJenkinsEnvVars(Map<String, String> envVars) {
        GitMetadata.Builder metadataBuilder = new GitMetadata.Builder();
        metadataBuilder.repositoryURL(getRepositoryUrlFromJenkinsEnvVars(envVars));
        metadataBuilder.branch(normalizeBranch(getBranchFromJenkinsEnvVars(envVars)));

        GitCommitMetadata.Builder commitMetadataBuilder = new GitCommitMetadata.Builder();
        commitMetadataBuilder.commit(envVars.get(GIT_COMMIT));
        commitMetadataBuilder.tag(normalizeTag(envVars.get(GIT_BRANCH)));
        metadataBuilder.commitMetadata(commitMetadataBuilder.build());

        return metadataBuilder.build();
    }

    private static String getRepositoryUrlFromJenkinsEnvVars(Map<String, String> envVars) {
        String repoUrl = envVars.get(GIT_REPOSITORY_URL);
        if (StringUtils.isNotBlank(repoUrl)) {
            return repoUrl;
        }
        return envVars.get(GIT_REPOSITORY_URL_ALT);
    }

    private static String getBranchFromJenkinsEnvVars(Map<String, String> envVars) {
        String branch = envVars.get(GIT_BRANCH);
        if (StringUtils.isNotBlank(branch)) {
            return branch;
        }
        return envVars.get(GIT_BRANCH_ALT);
    }

    public static GitMetadata buildGitMetadataWithUserSuppliedEnvVars(Map<String, String> envVars) {
        GitCommitMetadata.Builder commitMetadataBuilder = new GitCommitMetadata.Builder();
        commitMetadataBuilder.tag(envVars.get(DD_GIT_TAG));
        commitMetadataBuilder.commit(envVars.get(DD_GIT_COMMIT_SHA));
        commitMetadataBuilder.message(envVars.get(DD_GIT_COMMIT_MESSAGE));
        commitMetadataBuilder.authorName(envVars.get(DD_GIT_COMMIT_AUTHOR_NAME));
        commitMetadataBuilder.authorEmail(envVars.get(DD_GIT_COMMIT_AUTHOR_EMAIL));
        commitMetadataBuilder.committerName(envVars.get(DD_GIT_COMMIT_COMMITTER_NAME));
        commitMetadataBuilder.committerEmail(envVars.get(DD_GIT_COMMIT_COMMITTER_EMAIL));
        commitMetadataBuilder.authorDate(getDateIfValid(envVars, DD_GIT_COMMIT_AUTHOR_DATE));
        commitMetadataBuilder.committerDate(getDateIfValid(envVars, DD_GIT_COMMIT_COMMITTER_DATE));

        GitMetadata.Builder metadataBuilder = new GitMetadata.Builder();
        metadataBuilder.repositoryURL(envVars.get(DD_GIT_REPOSITORY_URL));
        metadataBuilder.defaultBranch(normalizeBranch(envVars.get(DD_GIT_DEFAULT_BRANCH)));
        metadataBuilder.branch(normalizeBranch(getBranchFromUserSuppliedEnvVars(envVars)));
        metadataBuilder.commitMetadata(commitMetadataBuilder.build());
        return metadataBuilder.build();
    }

    private static String getBranchFromUserSuppliedEnvVars(Map<String, String> envVars) {
        // we treat CHANGE_BRANCH as a user-supplied env var,
        // because it needs to have higher priority than the branch name
        // determined by the Git client
        // (because for PRs Git client uses something like "refs/remotes/origin/PR-1" as the ref name)
        String changeBranch = envVars.get(CHANGE_BRANCH);
        if (StringUtils.isNotBlank(changeBranch) && !isValidCommitSha(changeBranch)) {
            return changeBranch;
        }
        return envVars.get(DD_GIT_BRANCH);
    }

    private static String getDateIfValid(Map<String, String> envVars, String envVarName) {
        String envVar = envVars.get(envVarName);
        if (DatadogUtilities.isValidISO8601Date(envVar)) {
            return envVar;
        } else {
            if (StringUtils.isNotBlank(envVar)) {
                LOGGER.log(Level.WARNING, "Invalid date specified in " + envVarName + ": expected ISO8601 format (" + DatadogUtilities.toISO8601(new Date()) + "), got " + envVar);
            }
            return null;
        }
    }

    /**
     * Returns a normalized git tag
     * E.g: refs/heads/tags/0.1.0 or origin/tags/0.1.0 returns 0.1.0
     * @param tagName the tag name to normalize
     * @return normalized git tag
     */
    public static String normalizeTag(String tagName) {
        if(tagName == null || tagName.isEmpty() || !tagName.contains("tags")) {
            return null;
        }

        final String tagNameNoSlash = (tagName.startsWith("/")) ? tagName.replaceFirst("/", "") : tagName;
        return removeRefs(tagNameNoSlash).replace("tags/", "");
    }

    /**
     * Returns a normalized git branch
     * E.g. refs/heads/master or origin/master returns master
     * @param branchName the branch name to normalize
     * @return normalized git tag
     */
    public static String normalizeBranch(String branchName) {
        if (branchName == null || branchName.isEmpty() || branchName.contains("tags") || isValidCommitSha(branchName) || Constants.HEAD.equals(branchName)) {
            return null;
        }

        final String branchNameNoSlash = (branchName.startsWith("/")) ? branchName.replaceFirst("/", "") : branchName;
        return removeRefs(branchNameNoSlash);
    }

    private static String removeRefs(String gitReference) {
        if(gitReference.startsWith("origin/")) {
            return gitReference.replace("origin/", "");
        } else if(gitReference.startsWith("refs/heads/")) {
            return gitReference.replace("refs/heads/", "");
        } else if(gitReference.startsWith("refs/remotes/")) {
            // find the next slash after remotes/ to trim remote name ("origin" or anything else)
            int idx = gitReference.indexOf('/', "refs/remotes/".length());
            return idx >= 0 ? gitReference.substring(idx + 1) : gitReference.substring("refs/remotes/".length());
        }
        return gitReference;
    }

    /**
     * Filters the user info given a valid HTTP URL.
     * @param urlStr input URL
     * @return URL without user info.
     */
    public static String filterSensitiveInfo(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) {
            return urlStr;
        }

        try {
            final URI url = new URI(urlStr);
            final String userInfo = url.getRawUserInfo();
            return urlStr.replace(userInfo + "@", "");
        } catch (final URISyntaxException ex) {
            return urlStr;
        }
    }
}
