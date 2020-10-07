package org.datadog.jenkins.plugins.datadog.util.git;

import hudson.remoting.VirtualChannel;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

import java.io.IOException;

public final class RevCommitRepositoryCallback implements RepositoryCallback<RevCommit> {
    private static final long serialVersionUID = 1L;
    private final String commit;

    public RevCommitRepositoryCallback(final String commit) {
        this.commit = commit;
    }

    @Override
    public RevCommit invoke(Repository repository, VirtualChannel virtualChannel)
            throws IOException, InterruptedException {
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(repository.resolve(commit));
        }
    }
}
