package org.datadog.jenkins.plugins.datadog.util.git;

import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.io.Serializable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.eclipse.jgit.lib.*;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

/**
 * Populates GitMetadata.Builder instance for a certain repository
 * using the JGit.
 * <p>
 * This must be called using gitClient.withRepository(...) method.
 * See GitUtils.
 */
public final class GitMetadataBuilderCallback implements RepositoryCallback<GitMetadataBuilderCallback.Result> {

    private static transient final Logger LOGGER = Logger.getLogger(GitMetadataBuilderCallback.class.getName());
    private static final long serialVersionUID = 1L;

    @Override
    public Result invoke(Repository repository, VirtualChannel channel) {
        try {
            String remoteName = getRemoteName(repository);
            return new Result(
                getRepoUrl(repository, remoteName),
                getDefaultBranch(repository, remoteName),
                getBranch(repository) // currently checked out branch
            );

        } catch (Exception e) {
            DatadogUtilities.logException(LOGGER, Level.FINE, "Unable to build git metadata", e);
        }
        return null;
    }

    private static String getRepoUrl(Repository repository, String remoteName) {
        StoredConfig config = repository.getConfig();
        return config.getString("remote", remoteName, "url");
    }

    private static String getRemoteName(Repository repository) throws Exception {
        String remote = repository.getRemoteName(Constants.HEAD);
        if (remote != null) {
            return remote;
        }
        Ref head = repository.getRefDatabase().findRef(Constants.HEAD);
        String sha = head.getObjectId().getName();
        String shaRemote = repository.getRemoteName(sha);
        if (shaRemote != null) {
            return shaRemote;
        }
        Set<String> remoteNames = repository.getRemoteNames();
        if (!remoteNames.isEmpty()) {
            return remoteNames.iterator().next();
        }
        return Constants.DEFAULT_REMOTE_NAME;
    }

    private String getDefaultBranch(Repository repository, String remoteName) throws Exception {
        Ref remoteHead = repository.findRef("refs/remotes/" + remoteName + "/HEAD");
        if (remoteHead != null && remoteHead.isSymbolic()) {
            return remoteHead.getTarget().getName();
        }
        if (repository.findRef("master") != null || repository.findRef("refs/remotes/origin/master") != null) {
            return "master";
        }
        if (repository.findRef("main") != null || repository.findRef("refs/remotes/origin/main") != null) {
            return "main";
        }
        return null;
    }

    private static String getBranch(Repository repository) throws IOException {
        String branch = repository.getBranch();
        if (!GitUtils.isValidCommitSha(branch)) {
          return branch;
        }

        // A detached HEAD is checked out.
        // Iterate over available refs to see if any of them points to the checked out commit.
        for (Ref ref : repository.getRefDatabase().getRefs()) {
            String refName = ref.getName();
            if (Constants.HEAD.equals(refName) || GitUtils.isValidCommitSha(refName)) {
                continue;
            }
            ObjectId refObjectId = ref.getObjectId();
            if (branch.equals(refObjectId.getName())) {
                return refName;
            }
        }
        return null;
    }

    public static final class Result implements Serializable {
        final String repoUrl;
        final String defaultBranch;
        final String branch;

        public Result(String repoUrl, String defaultBranch, String branch) {
            this.repoUrl = repoUrl;
            this.defaultBranch = defaultBranch;
            this.branch = branch;
        }
    }
}
