package org.datadog.jenkins.plugins.datadog.util.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.FilePathUtils;

import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class GitUtils {

    private static transient final Logger LOGGER = Logger.getLogger(GitUtils.class.getName());
    private static transient final Pattern SHA1_PATTERN = Pattern.compile("\\b[a-f0-9]{40}\\b");

    private GitUtils(){}

    /**
     * Return the FilePath based on the Node name and the Workspace.
     * @param nodeName the node name to check
     * @param workspace the workspace to build the path
     * @return filePath for (nodeName, workspace)
     */
    public static FilePath buildFilePath(final String nodeName, final String workspace) {
        if(nodeName == null || workspace == null){
            LOGGER.fine("Unable to build FilePath. Either NodeName or Workspace is null");
            return null;
        }

        try {
            return "master".equals(nodeName) ? new FilePath(FilePath.localChannel, workspace):  FilePathUtils.find(nodeName, workspace);
        } catch (Exception e) {
            LOGGER.fine("Unable to build FilePath. Error: " + e);
            return null;
        }
    }

    /**
     * Return the FilePath associated with the run instance
     * @param run a particular execution of a Jenkins build
     * @return filePath for the run.
     */
    public static FilePath buildFilePath(final Run<?, ?> run){
        try {
            if(run == null) {
                LOGGER.fine("Unable to build FilePath. Run is null");
                return null;
            }

            final Executor executor = run.getExecutor();
            if(executor == null) {
                LOGGER.fine("Unable to build FilePath. Run executor is null");
                return null;
            }

            return executor.getCurrentWorkspace();
        } catch (Exception e) {
            LOGGER.fine("Unable to build FilePath. Error: " + e);
            return null;
        }
    }

    /**
     * Return the RevCommit for a certain commit based on the information
     * stored in a certain workspace of a certain node.
     * @param gitCommit the Git commit SHA to search info.
     * @return revCommit
     */
    public static RevCommit searchRevCommit(final GitClient gitClient, final String gitCommit) {
        try {
            if(gitClient == null) {
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
     * @param gitClient The Git client to use to obtain the repository information
     * @param envVars the env vars available.
     * @return repositoryInfo
     */
    public static RepositoryInfo searchRepositoryInfo(final GitClient gitClient, EnvVars envVars) {
        try {
            // Check if the default branch has been configured using an environment variable by the user.
            // This is needed because the automatic detection of the default branch using
            // the Git client is not always possible cause it depends on how Jenkins checkouts
            // the repository. Not always there is a symbolic reference to the default branch.
            final String defaultBranch = GitInfoUtils.normalizeBranch(envVars.get("DD_GIT_DEFAULT_BRANCH", null));
            LOGGER.fine("Detected default branch from environment variables: " + defaultBranch);
            if(defaultBranch != null && !defaultBranch.isEmpty()) {
                return new RepositoryInfo(defaultBranch);
            }

            if(gitClient == null){
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
     * Returns the GitCommitAction of the Run instance.
     * If the Run instance does not have GitCommitAction or
     * the current commit hash is different from the commit hash
     * stored in the GitCommitAction, then a new GitCommitAction
     * is built and stored in the Run instance.
     *
     * The GitCommit information is stored in an action because
     * it's fairly expensive to calculate. To avoid calculating
     * every time, it's store in the Run instance as an action.
     * @param run a particular execution of a Jenkins build
     * @param listener the task listener
     * @param envVars the env vars available
     * @param gitCommit the git commit SHA to use
     * @param nodeName the node name to use to build the Git client
     * @param workspace the workspace to use to build the Git client
     * @return the GitCommitAction with the information about Git Commit.
     */
    public static GitCommitAction buildGitCommitAction(Run<?, ?> run, TaskListener listener, EnvVars envVars, final String gitCommit, final String nodeName, final String workspace) {
        GitCommitAction commitAction = run.getAction(GitCommitAction.class);
        if(commitAction == null || !gitCommit.equals(commitAction.getCommit())) {
            try {
                final GitClient gitClient = GitUtils.newGitClient(run, listener, envVars, nodeName, workspace);
                if(gitClient == null){
                    LOGGER.fine("Unable to build GitCommitAction. GitClient is null");
                    return null;
                }

                final RevCommit revCommit = GitUtils.searchRevCommit(gitClient, gitCommit);
                if(revCommit == null) {
                    LOGGER.fine("Unable to build GitCommitAction. RevCommit is null. [gitCommit: "+gitCommit+"]");
                    return null;
                }

                final GitCommitAction.Builder builder = GitCommitAction.newBuilder();
                builder.withCommit(gitCommit);
                String message;
                try {
                    message = StringUtils.abbreviate(revCommit.getFullMessage(), 1500);
                } catch (Exception e) {
                    LOGGER.fine("Unable to obtain git commit full message. Selecting short message. Error: " + e);
                    message = revCommit.getShortMessage();
                }
                builder.withMessage(message);

                final PersonIdent authorIdent = revCommit.getAuthorIdent();
                if(authorIdent != null){
                    builder.withAuthorName(authorIdent.getName())
                            .withAuthorEmail(authorIdent.getEmailAddress())
                            .withAuthorDate(DatadogUtilities.toISO8601(authorIdent.getWhen()));
                }

                final PersonIdent committerIdent = revCommit.getCommitterIdent();
                if(committerIdent != null) {
                    builder.withCommitterName(committerIdent.getName())
                            .withCommitterEmail(committerIdent.getEmailAddress())
                            .withCommitterDate(DatadogUtilities.toISO8601(committerIdent.getWhen()));
                }

                commitAction = builder.build();
                run.addOrReplaceAction(commitAction);
            } catch (Exception e) {
                LOGGER.fine("Unable to build GitCommitAction. Error: " + e);
            }
        }
        return commitAction;
    }

    /**
     * Returns the GitRepositoryAction of the Run instance.
     * If the Run instance does not have GitRepositoryAction or
     * some infor is not populated in the GitRepositoryAction,
     * then a new GitCommitAction is built and stored in the Run instance.
     *
     * The GitRepository information is stored in an action because
     * it's fairly expensive to calculate. To avoid calculating
     * every time, it's store in the Run instance as an action.
     * @param run a particular execution of a Jenkins build
     * @param listener the task listener
     * @param envVars the env vars available
     * @param nodeName the node name to use to build the Git client
     * @param workspace the workspace to use to build the Git client
     * @return the GitRepositoryAction with the information about Git repository.
     */
    public static GitRepositoryAction buildGitRepositoryAction(Run<?, ?> run, TaskListener listener, EnvVars envVars, final String nodeName, final String workspace) {
        GitRepositoryAction repoAction = run.getAction(GitRepositoryAction.class);
        if(repoAction == null || repoAction.getDefaultBranch() == null) {
            try {
                final GitClient gitClient = GitUtils.newGitClient(run, listener, envVars, nodeName, workspace);
                if(gitClient == null){
                    LOGGER.fine("Unable to build GitRepositoryAction. GitClient is null");
                    return null;
                }

                final RepositoryInfo repositoryInfo = GitUtils.searchRepositoryInfo(gitClient, envVars);
                if(repositoryInfo == null) {
                    LOGGER.fine("Unable to build GitRepositoryAction. RepositoryInfo is null");
                    return null;
                }

                final GitRepositoryAction.Builder builder = GitRepositoryAction.newBuilder();
                builder.withDefaultBranch(repositoryInfo.getDefaultBranch());

                repoAction = builder.build();
                run.addOrReplaceAction(repoAction);
            } catch (Exception e) {
                LOGGER.fine("Unable to build GitRepositoryAction. Error: " + e);
            }
        }
        return repoAction;
    }

    /**
     * Creates a new instance of a {@code GitClient}.
     * @param run a particular execution of a Jenkins build
     * @param listener the task listener
     * @param envVars the env vars available
     * @param nodeName the node name to use to build the Git client
     * @param workspace the workspace to use to build the Git client
     * @return gitClient
     */
    public static GitClient newGitClient(final Run<?,?> run, final TaskListener listener, final EnvVars envVars, final String nodeName, final String workspace) {
        try {
            FilePath ws = GitUtils.buildFilePath(run);
            if(ws == null){
                ws = GitUtils.buildFilePath(nodeName, workspace);
            }

            if(ws == null) {
                return null;
            }

            final Git git = Git.with(listener, envVars).in(ws);
            return git.getClient();
        } catch (Exception e) {
            LOGGER.fine("Unable to create GitClient. Error: " + e);
            return null;
        }
    }

    /**
     * Check if the git commit is a valid commit.
     * @param gitCommit the git commit to evaluate
     * @return true if the git commit is a valid SHA 40 (HEX)
     */
    public static boolean isValidCommit(String gitCommit) {
        if(gitCommit == null || gitCommit.isEmpty()) {
            return false;
        }

        if(gitCommit.length() != 40) {
            return false;
        }

        return SHA1_PATTERN.matcher(gitCommit).matches();
    }
}
