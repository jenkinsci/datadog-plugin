/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package org.datadog.jenkins.plugins.datadog.listeners;

import static org.datadog.jenkins.plugins.datadog.events.SCMCheckoutCompletedEventImpl.SCM_CHECKOUT_COMPLETED_EVENT_NAME;
import static org.datadog.jenkins.plugins.datadog.util.git.GitConstants.DD_GIT_DEFAULT_BRANCH;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogJobProperty;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.audit.DatadogAudit;
import org.datadog.jenkins.plugins.datadog.clients.ClientFactory;
import org.datadog.jenkins.plugins.datadog.events.SCMCheckoutCompletedEventImpl;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.datadog.jenkins.plugins.datadog.traces.GitInfoUtils;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
import org.datadog.jenkins.plugins.datadog.util.git.RepositoryInfo;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * This class registers an {@link SCMListener} with Jenkins which allows us to create
 * the "Checkout successful" event.
 */
@Extension
public class DatadogSCMListener extends SCMListener {

    private static final Logger logger = Logger.getLogger(DatadogSCMListener.class.getName());

    /**
     * Invoked right after the source code for the build has been checked out. It will NOT be
     * called if a checkout fails.
     *
     * @param build           - Current build
     * @param scm             - Configured SCM
     * @param workspace       - Current workspace
     * @param listener        - Current build listener
     * @param changelogFile   - Changelog
     * @param pollingBaseline - Polling
     * @throws Exception if an error is encountered
     */
    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                           File changelogFile, SCMRevisionState pollingBaseline) throws Exception {
        try {
            // Process only if job is NOT in excluded and is in included
            if (!DatadogUtilities.isJobTracked(build.getParent().getFullName())) {
                return;
            }

            logger.fine("Start DatadogSCMListener#onCheckout");

            if (isGit(scm)) {
                EnvVars environment = build.getEnvironment(listener);
                GitClient gitClient = GitUtils.newGitClient(listener, environment, workspace);
                populateCommitInfo(build, gitClient);
                populateRepositoryInfo(build, gitClient, environment);
            } else {
                logger.fine("Will not populate git commit and repository info for non-git SCM: "
                        + (scm != null ? scm.getType() : null));
            }

            DatadogJobProperty prop = DatadogUtilities.getDatadogJobProperties(build);
            if (prop == null || !prop.isEmitSCMEvents()) {
                return;
            }

            // Get Datadog Client Instance
            DatadogClient client = ClientFactory.getClient();
            if (client == null) {
                return;
            }

            // Collect Build Data
            BuildData buildData;
            try {
                buildData = new BuildData(build, listener);
            } catch (IOException | InterruptedException e) {
                DatadogUtilities.severe(logger, e, "Failed to parse checked out build data");
                return;
            }

            // Send event
            boolean shouldSendEvent = DatadogUtilities.shouldSendEvent(SCM_CHECKOUT_COMPLETED_EVENT_NAME);
            if (shouldSendEvent) {
                DatadogEvent event = new SCMCheckoutCompletedEventImpl(buildData);
                client.event(event);
            }

            // Submit counter
            String hostname = DatadogUtilities.getHostname(null);
            Map<String, Set<String>> tags = buildData.getTags();
            client.incrementCounter("jenkins.scm.checkout", hostname, tags);

            logger.fine("End DatadogSCMListener#onCheckout");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process build checkout event");
        }
    }

    private boolean isGit(SCM scm) {
        if (scm == null) {
            return false;
        }
        String scmType = scm.getType();
        return scmType != null && scmType.toLowerCase().contains("git");
    }

    private void populateCommitInfo(final Run<?, ?> run, @Nullable final GitClient gitClient) {
        long start = System.currentTimeMillis();
        try {
            GitCommitAction commitAction = run.getAction(GitCommitAction.class);
            if (commitAction == null) {
                logger.fine("Unable to get git commit information. GitCommitAction is null.");
                return;
            }

            String gitCommit = commitAction.getCommit();
            if (gitCommit == null) {
                gitCommit = "HEAD";
            }

            final RevCommit revCommit = GitUtils.searchRevCommit(gitClient, gitCommit);
            if (revCommit == null) {
                logger.fine("Unable to get git commit information. RevCommit is null. [gitCommit: " + gitCommit + "]");
                return;
            }

            commitAction.setCommit(revCommit.getName());

            String message;
            try {
                message = StringUtils.abbreviate(revCommit.getFullMessage(), 1500);
            } catch (Exception e) {
                logger.fine("Unable to obtain git commit full message. Selecting short message. Error: " + e);
                message = revCommit.getShortMessage();
            }
            commitAction.setMessage(message);

            final PersonIdent authorIdent = revCommit.getAuthorIdent();
            if (authorIdent != null) {
                commitAction.setAuthorName(authorIdent.getName());
                commitAction.setAuthorEmail(authorIdent.getEmailAddress());
                commitAction.setAuthorDate(DatadogUtilities.toISO8601(authorIdent.getWhen()));
            }

            final PersonIdent committerIdent = revCommit.getCommitterIdent();
            if (committerIdent != null) {
                commitAction.setCommitterName(committerIdent.getName());
                commitAction.setCommitterEmail(committerIdent.getEmailAddress());
                commitAction.setCommitterDate(DatadogUtilities.toISO8601(committerIdent.getWhen()));
            }

        } catch (Exception e) {
            logger.fine("Unable to get git commit information. Error: " + e);

        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("GitUtils.buildGitCommitAction", start, end);
        }
    }

    private void populateRepositoryInfo(final Run<?, ?> run, @Nullable final GitClient gitClient, final EnvVars environment) {
        long start = System.currentTimeMillis();
        try {
            GitRepositoryAction repoAction = run.getAction(GitRepositoryAction.class);
            if (repoAction == null) {
                logger.fine("Unable to get git repo information. GitCommitAction is null.");
                return;
            }

            populateRepositoryInfoFromEnvVars(environment, repoAction);

            RepositoryInfo repositoryInfo = GitUtils.searchRepositoryInfo(gitClient);
            if (repositoryInfo == null) {
                logger.fine("Unable to build GitRepositoryAction. RepositoryInfo is null");
                return;
            }

            if (repoAction.getRepositoryURL() != null && !repoAction.getRepositoryURL().equals(repositoryInfo.getRepoUrl())) {
                logger.fine("Git repo URL differs, stored in action: " + repoAction.getRepositoryURL() + ", found: " + repositoryInfo.getRepoUrl());
                return;
            }

            if (repoAction.getRepositoryURL() == null) {
                repoAction.setRepositoryURL(repositoryInfo.getRepoUrl());
            }

            if (repoAction.getBranch() == null) {
                repoAction.setBranch(repositoryInfo.getBranch());
            }

            if (repoAction.getDefaultBranch() == null) {
                repoAction.setDefaultBranch(repositoryInfo.getDefaultBranch());
            }

        } catch (Exception e) {
            logger.fine("Unable to get git repo information. Error: " + e);

        } finally {
            long end = System.currentTimeMillis();
            DatadogAudit.log("GitUtils.buildGitRepositoryAction", start, end);
        }
    }

    /** This duplicates logic available in step listener, because step listener is not called for freestyle jobs. */
    private static void populateRepositoryInfoFromEnvVars(EnvVars environment, GitRepositoryAction repoAction) {
        final String gitUrl = GitUtils.resolveGitRepositoryUrl(environment);
        if (gitUrl != null && !gitUrl.isEmpty()) {
            repoAction.setRepositoryURL(gitUrl);
        }

        final String defaultBranch = GitInfoUtils.normalizeBranch(environment.get(DD_GIT_DEFAULT_BRANCH));
        if (defaultBranch != null && !defaultBranch.isEmpty()) {
            repoAction.setDefaultBranch(defaultBranch);
        }

        final String gitBranch = GitUtils.resolveGitBranch(environment);
        if(gitBranch != null && !gitBranch.isEmpty()) {
            repoAction.setBranch(gitBranch);
        }
    }

}
