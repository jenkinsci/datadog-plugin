package org.datadog.jenkins.plugins.datadog.util.git;

import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_BRANCH;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_COMMIT_SHA;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_REPOSITORY_URL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_TAG;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.GIT_BRANCH;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.GIT_COMMIT;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.GIT_REPOSITORY_URL;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.GIT_REPOSITORY_URL_ALT;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.USER_SUPPLIED_GIT_ENVVARS;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.audit.DatadogAudit;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

public final class GitUtils {

    private static transient final Logger LOGGER = Logger.getLogger(GitUtils.class.getName());
    private static transient final Pattern SHA1_PATTERN = Pattern.compile("\\b[a-f0-9]{40}\\b");
    private static transient final Pattern SCP_REPO_URI_REGEX = Pattern.compile("^([\\w.~-]+@)?(?<host>[\\w.-]+):(?<path>[\\w./-]+)(?:\\?|$)(.*)$");

    private GitUtils() {
    }

    /**
     * Return the RevCommit for a certain commit based on the information
     * stored in a certain workspace of a certain node.
     *
     * @param gitCommit the Git commit SHA to search info.
     * @param gitClient the Git client used.
     * @return revCommit
     */
    public static RevCommit searchRevCommit(final GitClient gitClient, final String gitCommit) {
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
     * Return the {@code RepositoryInfo} for a certain Git repository.
     *
     * @param gitClient The Git client to use to obtain the repository information
     * @return repositoryInfo
     */
    public static RepositoryInfo searchRepositoryInfo(final GitClient gitClient) {
        try {
            if (gitClient == null) {
                LOGGER.fine("Unable to search RevCommit. GitClient is null");
                return null;
            }

            return gitClient.withRepository(new RepositoryInfoCallback());
        } catch (Exception e) {
            LOGGER.fine("Unable to search Repository Info. Error: " + e);
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

    /**
     * Check if the git commit is a valid commit.
     *
     * @param gitCommit the git commit to evaluate
     * @return true if the git commit is a valid SHA 40 (HEX)
     */
    public static boolean isValidCommit(String gitCommit) {
        if (gitCommit == null || gitCommit.isEmpty()) {
            return false;
        }

        if (gitCommit.length() != 40) {
            return false;
        }

        return SHA1_PATTERN.matcher(gitCommit).matches();
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

    /**
     * Check if the GitRepositoryAction has been already created and populated.
     * Typically this method is used to avoid calculating the action multiple times.
     *
     * @param run              the current run
     * @param gitRepositoryUrl the current git respository
     * @return true if the action has been created and populated.
     */
    public static boolean isRepositoryInfoAlreadyCreated(Run<?, ?> run, final String gitRepositoryUrl) {
        final GitRepositoryAction repositoryAction = run.getAction(GitRepositoryAction.class);
        return repositoryAction != null && repositoryAction.getRepositoryURL() != null && repositoryAction.getRepositoryURL().equals(gitRepositoryUrl);
    }

    /**
     * Check if the GitCommitAction has been already created and populated.
     * Typically this method is used to avoid calculating the action multiple times.
     *
     * @param run       the current run
     * @param gitCommit the git commit to check for
     * @return true if the action has been created and populated.
     */
    public static boolean isCommitInfoAlreadyCreated(Run<?, ?> run, final String gitCommit) {
        GitCommitAction commitAction = run.getAction(GitCommitAction.class);
        return commitAction != null && commitAction.getCommit() != null && commitAction.getCommit().equals(gitCommit);
    }

    /**
     * Resolve the value for the git branch based
     * 1: Check user supplied env var
     * 2: Check Jenkins env var
     * 3: Check BuildData already calculated
     *
     * @param envVars the user supplied env vars
     * @return the branch value.
     */
    public static String resolveGitBranch(Map<String, String> envVars) {
        if (StringUtils.isNotEmpty(envVars.get(DD_GIT_BRANCH))) {
            return envVars.get(DD_GIT_BRANCH);
        } else if (StringUtils.isNotEmpty(envVars.get(GIT_BRANCH))) {
            return envVars.get(GIT_BRANCH);
        } else {
            return null;
        }
    }

    /**
     * Resolve the value for the git commit sha based
     * 1: Check user supplied env var
     * 2: Check Jenkins env var
     * 3: Check BuildData already calculated
     *
     * @param envVars the user supplied env vars
     * @return the commit sha value.
     */
    public static String resolveGitCommit(Map<String, String> envVars) {
        if (isValidCommit(envVars.get(DD_GIT_COMMIT_SHA))) {
            return envVars.get(DD_GIT_COMMIT_SHA);
        } else if (isValidCommit(envVars.get(GIT_COMMIT))) {
            return envVars.get(GIT_COMMIT);
        } else {
            return null;
        }
    }

    /**
     * Resolve the value for the git repository url based
     * 1: Check user supplied env var
     * 2: Check Jenkins env var
     * 3: Check BuildData already calculated
     *
     * @param envVars the user supplied env vars
     * @return the git repository url value.
     */
    public static String resolveGitRepositoryUrl(Map<String, String> envVars) {
        if (StringUtils.isNotEmpty(envVars.get(DD_GIT_REPOSITORY_URL))) {
            return envVars.get(DD_GIT_REPOSITORY_URL);
        } else if (StringUtils.isNotEmpty(envVars.get(GIT_REPOSITORY_URL))) {
            return envVars.get(GIT_REPOSITORY_URL);
        } else if (StringUtils.isNotEmpty(envVars.get(GIT_REPOSITORY_URL_ALT))) {
            return envVars.get(GIT_REPOSITORY_URL_ALT);
        } else {
            return null;
        }
    }

    /**
     * Resolve the value for the git tag based
     * 1: Check user supplied env var
     * 3: Check BuildData already calculated
     *
     * @param envVars the user supplied environment variables
     * @return the git tag value.
     */
    public static String resolveGitTag(Map<String, String> envVars) {
        if (StringUtils.isNotEmpty(envVars.get(DD_GIT_TAG))) {
            return envVars.get(DD_GIT_TAG);
        } else {
            return null;
        }
    }

    /**
     * Check if the env vars map contains any environment variable with Git information supplied by the user manually.
     *
     * @param envVars the environment variables
     * @return true if any of the env vars is not empty.
     */
    public static boolean isUserSuppliedGit(Map<String, String> envVars) {
        if (envVars == null) {
            return false;
        }

        for (final String key : USER_SUPPLIED_GIT_ENVVARS) {
            if (StringUtils.isNotEmpty(envVars.get(key))) {
                return true;
            }
        }
        return false;
    }
}
