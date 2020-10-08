package org.datadog.jenkins.plugins.datadog.util.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.workflow.FilePathUtils;

import java.util.logging.Logger;

public final class GitUtils {

    private static transient final Logger LOGGER = Logger.getLogger(GitUtils.class.getName());

    private GitUtils(){}

    /**
     * Return the FilePath based on the Node name and the Workspace.
     * @param nodeName
     * @param workspace
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
     * @param run
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
                LOGGER.fine("Unable to build FilePath. Run is null");
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
     * @param run
     * @param listener
     * @param envVars
     * @param gitCommit
     * @param nodeName
     * @param workspace
     * @return revCommit
     */
    public static RevCommit searchRevCommit(final Run<?,?> run, final TaskListener listener, final EnvVars envVars, final String gitCommit, final String nodeName, final String workspace) {
        try {
            FilePath ws = GitUtils.buildFilePath(run);
            if(ws == null){
                ws = GitUtils.buildFilePath(nodeName, workspace);
            }

            if(ws == null) {
                return null;
            }

            final Git git = Git.with(listener, envVars).in(ws);
            return git.getClient().withRepository(new RevCommitRepositoryCallback(gitCommit));
        } catch (Exception e) {
            LOGGER.fine("Unable to search RevCommit. Error: " + e);
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
     * @param run
     * @param listener
     * @param envVars
     * @param gitCommit
     * @param nodeName
     * @param workspace
     * @return
     */
    public static GitCommitAction buildGitCommitAction(Run<?, ?> run, TaskListener listener, EnvVars envVars, final String gitCommit, final String nodeName, final String workspace) {
        GitCommitAction commitAction = run.getAction(GitCommitAction.class);
        if(commitAction == null || !gitCommit.equals(commitAction.getCommit())) {
            try {
                final RevCommit revCommit = GitUtils.searchRevCommit(run, listener, envVars, gitCommit, nodeName, workspace);
                if(revCommit == null) {
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

}
