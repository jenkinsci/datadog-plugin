package org.datadog.jenkins.plugins.datadog.util.git;

import hudson.remoting.VirtualChannel;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

import java.io.IOException;

/**
 * Returns the RevCommit instance for a certain commit
 * using the JGit.
 *
 * This must be called using gitClient.withRepository(...) method.
 * See GitUtils.
 */
public final class RevCommitRepositoryCallback implements RepositoryCallback<RevCommit> {
    private static final long serialVersionUID = 1L;
    private final String commit;

    public RevCommitRepositoryCallback(final String commit) {
        this.commit = commit;
    }

    @Override
    public RevCommit invoke(final Repository repository, final VirtualChannel virtualChannel) throws IOException {
        if(this.commit == null || this.commit.isEmpty()) {
            return null;
        }

        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(repository.resolve(commit));
        }
    }
}
