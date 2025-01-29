package org.datadog.jenkins.plugins.datadog.util.git;

import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.normalizeBranch;

import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.eclipse.jgit.lib.*;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    private final String branchHint;
    private final boolean examineRepoRefs;

    public GitMetadataBuilderCallback(@Nullable String branchHint, boolean examineRepoRefs) {
        this.branchHint = branchHint;
        this.examineRepoRefs = examineRepoRefs;
    }

  /**
     * !IMPORTANT!
     * Keep in mind that this callback may be executed on a worker node,
     * which means both the callback and the result are serialized and sent between different hosts.
     * Avoid using anything that is not serializable
     */
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

    /**
     * When checking out a repository for a multi-branch pipeline,
     * Jenkins does so in "detached HEAD" state.
     * This means that a specific commit is checked out, rather than a branch.
     * To work around this the plugin examines available branches
     * to see which one of them points to the checked out commit.
     * <p>
     * It is possible that multiple branches point to the same commit.
     * In which case the logic in this method tries to use a "hint" extracted from environment variables.
     */
    private String getBranch(Repository repository) throws IOException {
        String branchOrSha = repository.getBranch();
        String branch = normalizeBranch(branchOrSha);
        if (branch != null) {
            return branch;
        }

        if (!examineRepoRefs) {
            return null;
        }

        String bestEffortBranch = null;
        RefDatabase refDatabase = repository.getRefDatabase();
        Set<Ref> refsPointingToSha = refDatabase.getTipsWithSha1(ObjectId.fromString(branchOrSha));
        for (Ref ref : refsPointingToSha) {
            String refName = normalizeBranch(ref.getName());
            if (refName == null) {
                continue;
            }
            if (refName.equals(branchHint)) {
                // Found a branch that has the same name as the one set in env vars
                return refName;
            }
            bestEffortBranch = refName;
        }
        return bestEffortBranch;
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
