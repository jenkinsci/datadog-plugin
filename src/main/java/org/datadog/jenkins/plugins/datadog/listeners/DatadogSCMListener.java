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

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.slaves.WorkspaceList;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.datadog.jenkins.plugins.datadog.DatadogClient;
import org.datadog.jenkins.plugins.datadog.DatadogEvent;
import org.datadog.jenkins.plugins.datadog.DatadogJobProperty;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.events.SCMCheckoutCompletedEventImpl;
import org.datadog.jenkins.plugins.datadog.metrics.Metrics;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.GitMetadataAction;
import org.datadog.jenkins.plugins.datadog.model.git.Source;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriter;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriterFactory;
import org.datadog.jenkins.plugins.datadog.util.git.GitUtils;
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
     */
    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
                           File changelogFile, SCMRevisionState pollingBaseline) {
        try {
            if (isSharedLibraryCheckout(build, workspace)) {
                // We do not want to tag traces with shared library Git info
                return;
            }

            // Process only if job is NOT in excluded and is in included
            if (!DatadogUtilities.isJobTracked(build)) {
                return;
            }

            logger.fine("Start DatadogSCMListener#onCheckout");

            GitMetadataAction gitMetadataAction = build.getAction(GitMetadataAction.class);
            if (gitMetadataAction != null) {
                EnvVars environment = build.getEnvironment(listener);
                gitMetadataAction.addMetadata(Source.JENKINS_ENV_VARS, GitUtils.buildGitMetadataWithJenkinsEnvVars(environment));
                gitMetadataAction.addMetadata(Source.USER_SUPPLIED_ENV_VARS, GitUtils.buildGitMetadataWithUserSuppliedEnvVars(environment));

                if (isGit(scm)) {
                    GitClient gitClient = GitUtils.newGitClient(listener, environment, workspace);
                    Source metadataSource = isPipelineScriptClone(workspace) ? Source.GIT_CLIENT_PIPELINE_DEFINITION : Source.GIT_CLIENT;
                    gitMetadataAction.addMetadata(metadataSource, GitUtils.buildGitMetadata(gitClient));
                } else {
                    logger.fine("Non-git SCM checkout: " + (scm != null ? scm.getType() : null));
                }
            }

            BuildData buildData = BuildData.create(build, listener);

            // We have Git info available now - submit a pipeline event so the backend could update its data
            if (DatadogUtilities.getDatadogGlobalDescriptor().getEnableCiVisibility()) {
                TraceWriter traceWriter = TraceWriterFactory.getTraceWriter();
                if (traceWriter != null) {
                    traceWriter.submitBuild(buildData, build);
                }
            }

            DatadogJobProperty prop = DatadogUtilities.getDatadogJobProperties(build);
            if (prop == null || !prop.isEmitSCMEvents()) {
                return;
            }

            // Get Datadog Client Instance
            DatadogClient client = ClientHolder.getClient();
            if (client == null) {
                return;
            }

            // Send event
            boolean shouldSendEvent = DatadogUtilities.shouldSendEvent(SCM_CHECKOUT_COMPLETED_EVENT_NAME);
            if (shouldSendEvent) {
                DatadogEvent event = new SCMCheckoutCompletedEventImpl(buildData);
                client.event(event);
            }

            String hostname = DatadogUtilities.getHostname(null);
            Map<String, Set<String>> tags = buildData.getTags();
            Metrics.getInstance().incrementCounter("jenkins.scm.checkout", hostname, tags);

            logger.fine("End DatadogSCMListener#onCheckout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Interrupted while trying to process build checkout event");
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to process build checkout event");
        }
    }

    private static boolean isSharedLibraryCheckout(Run<?, ?> build, FilePath workspace) {
      try {
          return hasLibrariesAction(build) && (isCommonSharedLibraryClone(workspace) || isFreshSharedLibraryClone(build, workspace));
      } catch (Exception e) {
          DatadogUtilities.severe(logger, e, "Failed to check if checkout is a shared library: " + workspace);
          return false;
      }
    }

    /**
     * Verifies that a build has Libraries action associated with it.
     * Acts as additional safety/sanity check, because the other use heuristics based on libraries folder structure
     */
    private static boolean hasLibrariesAction(Run<?, ?> build) {
        for (Action action : build.getAllActions()) {
            // using class name instead of class literal, as the class is not public
            if (action.getClass().getName().equals("org.jenkinsci.plugins.workflow.libs.LibrariesAction")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if workspace correspond to a shared library that has "Fresh clone per build" setting enabled
     */
    static boolean isFreshSharedLibraryClone(Run<?, ?> build, FilePath workspace) {
        // example of workspace: <JENKINS_HOME>/jobs/<PIPELINE_NAME>/builds/<BUILD_NUMBER>/libs/<LIBRARY_FOLDER>/root
        Path rootPath = build.getRootDir().toPath();
        Path workspacePath = Paths.get(workspace.getRemote());
        Path relativePath = rootPath.relativize(workspacePath);
        return relativePath.startsWith("libs");
    }

    /**
     * Returns true if workspace correspond to a shared library that does NOT have "Fresh clone per build" setting enabled
     */
    static boolean isCommonSharedLibraryClone(FilePath workspace) {
        // example of workspace: <JENKINS_HOME>/workspace/<PIPELINE_NAME>@libs/<LIBRARY_FOLDER>
        if (workspace == null){
            return false;
        }
        FilePath parent = workspace.getParent();
        if (parent == null) {
            return false;
        }
        String name = parent.getName();
        return name.endsWith(FILE_PATH_SUFFIX + "libs");
    }

    /**
     * Returns true if workspace correspond to a multi-branch pipeline script
     */
    static boolean isPipelineScriptClone(FilePath workspace) {
        // example of workspace: <JENKINS_HOME>/workspace/<PIPELINE_NAME>@script/<SCRIPT_FOLDER>
        if (workspace == null){
            return false;
        }
        FilePath parent = workspace.getParent();
        if (parent == null) {
            return false;
        }
        String name = parent.getName();
        return name.endsWith(FILE_PATH_SUFFIX + "script");
    }

    // Taken from https://github.com/jenkinsci/pipeline-groovy-lib-plugin/blob/master/src/main/java/org/jenkinsci/plugins/workflow/libs/SCMBasedRetriever.java#L250
    // Also see https://docs.cloudbees.com/docs/cloudbees-ci-kb/latest/troubleshooting-guides/changing-the-at-separator-character-for-workspace-folders-when-concurrent-builds-are-enabled
    private static final String FILE_PATH_SUFFIX = System.getProperty(WorkspaceList.class.getName(), "@");

    private boolean isGit(SCM scm) {
        if (scm == null) {
            return false;
        }
        String scmType = scm.getType();
        return scmType != null && scmType.toLowerCase().contains("git");
    }
}
