package org.datadog.jenkins.plugins.datadog.util.git;

import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils;
import org.eclipse.jgit.lib.*;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

/**
 * Returns the RepositoryInfo instance for a certain repository
 * using the JGit.
 * <p>
 * This must be called using gitClient.withRepository(...) method.
 * See GitUtils.
 */
public final class RepositoryInfoCallback implements RepositoryCallback<RepositoryInfo> {

    private static transient final Logger LOGGER = Logger.getLogger(RepositoryInfoCallback.class.getName());
    private static final long serialVersionUID = 1L;

    @Override
    public RepositoryInfo invoke(Repository repository, VirtualChannel channel) {
        try {
            String remoteName = getRemoteName(repository);
            String repoUrl = getRepoUrl(repository, remoteName);
            final String defaultBranch = getDefaultBranch(repository, remoteName);
            String branch = GitInfoUtils.normalizeBranch(getBranch(repository)); // currently checked out branch
            return new RepositoryInfo(repoUrl, defaultBranch, branch);
        } catch (Exception e) {
            LOGGER.fine("Unable to build RepositoryInfo. Error: " + e);
            return null;
        }
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
            return GitInfoUtils.normalizeBranch(remoteHead.getTarget().getName());
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
        if (!GitInfoUtils.isSha(branch)) {
          return branch;
        }

        // A detached HEAD is checked out.
        // Iterate over available refs to see if any of them points to the checked out commit.
        for (Ref ref : repository.getRefDatabase().getRefs()) {
            String refName = ref.getName();
            if (Constants.HEAD.equals(refName) || GitInfoUtils.isSha(branch)) {
                continue;
            }
            ObjectId refObjectId = ref.getObjectId();
            if (branch.equals(refObjectId.getName())) {
                return refName;
            }
        }
        return null;
    }
}
