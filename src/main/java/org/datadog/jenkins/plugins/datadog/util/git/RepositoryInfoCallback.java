package org.datadog.jenkins.plugins.datadog.util.git;

import hudson.remoting.VirtualChannel;
import org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Returns the RepositoryInfo instance for a certain repository
 * using the JGit.
 *
 * This must be called using gitClient.withRepository(...) method.
 * See GitUtils.
 */
public final class RepositoryInfoCallback implements RepositoryCallback<RepositoryInfo> {

    private static transient final Logger LOGGER = Logger.getLogger(RepositoryInfoCallback.class.getName());
    private static final long serialVersionUID = 1L;

    @Override
    public RepositoryInfo invoke(Repository repository, VirtualChannel channel) throws IOException, InterruptedException {
        try {
            System.out.println("Repository: " + repository);
            Ref head = repository.getRefDatabase().findRef("HEAD");
            if(head == null) {
                LOGGER.info("Unable to build RepositoryInfo. HEAD is null.");
                return RepositoryInfo.EMPTY_REPOSITORY_INFO;
            }

            // Discarded if it's not a symbolic to refs.
            if(!head.isSymbolic()) {
                LOGGER.info("Unable to build RepositoryInfo. HEAD is not symbolic.");
                LOGGER.info("HEAD: " + head);
                return RepositoryInfo.EMPTY_REPOSITORY_INFO;
            }

            final String defaultBranch = GitInfoUtils.normalizeBranch(head.getTarget().getName());
            return new RepositoryInfo(defaultBranch);

        } catch (Exception e) {
            LOGGER.info("Unable to build RepositoryInfo. Error: " + e);
            return null;
        }
    }
}
