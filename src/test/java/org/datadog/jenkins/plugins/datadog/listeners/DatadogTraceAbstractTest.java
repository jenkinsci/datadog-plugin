package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import hudson.model.Run;
import java.util.Map;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineQueueInfoAction;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;

public abstract class DatadogTraceAbstractTest {

    protected void assertGitVariablesOnSpan(TraceSpan span, String defaultBranch, String localGitRepoPath) {
        final Map<String, String> meta = span.getMeta();
        assertEquals("Initial commit\n", meta.get(CITags.GIT_COMMIT_MESSAGE));
        assertEquals("John Doe", meta.get(CITags.GIT_COMMIT_AUTHOR_NAME));
        assertEquals("john@doe.com", meta.get(CITags.GIT_COMMIT_AUTHOR_EMAIL));
        assertEquals("2020-10-08T07:49:32.000Z", meta.get(CITags.GIT_COMMIT_AUTHOR_DATE));
        assertEquals("John Doe", meta.get(CITags.GIT_COMMIT_COMMITTER_NAME));
        assertEquals("john@doe.com", meta.get(CITags.GIT_COMMIT_COMMITTER_EMAIL));
        assertEquals("2020-10-08T07:49:32.000Z", meta.get(CITags.GIT_COMMIT_COMMITTER_DATE));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT__SHA));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get(CITags.GIT_COMMIT_SHA));
        assertEquals("master", meta.get(CITags.GIT_BRANCH));
        assertEquals("file://" + localGitRepoPath, meta.get(CITags.GIT_REPOSITORY_URL));
        assertEquals(defaultBranch, meta.get(CITags.GIT_DEFAULT_BRANCH));
    }

    protected void assertGitVariablesOnWebhook(JSONObject webhook, String defaultBranch, String localGitRepoPath) {
        JSONObject meta = webhook.getJSONObject("git");
        assertEquals("Initial commit\n", meta.get("message"));
        assertEquals("John Doe", meta.get("author_name"));
        assertEquals("john@doe.com", meta.get("author_email"));
        assertEquals("2020-10-08T07:49:32.000Z", meta.get("author_time"));
        assertEquals("John Doe", meta.get("committer_name"));
        assertEquals("john@doe.com", meta.get("committer_email"));
        assertEquals("2020-10-08T07:49:32.000Z", meta.get("commit_time"));
        assertEquals("401d997a6eede777602669ccaec059755c98161f", meta.get("sha"));
        assertEquals("master", meta.get("branch"));
        assertEquals("file://" + localGitRepoPath, meta.get("repository_url"));
        assertEquals(defaultBranch, meta.get("default_branch"));
    }

    protected void assertCleanupActions(Run<?,?> run) {
        assertNull(run.getAction(BuildSpanAction.class));
        assertNull(run.getAction(GitCommitAction.class));
        assertNull(run.getAction(GitRepositoryAction.class));
        assertNull(run.getAction(PipelineNodeInfoAction.class));
        assertNull(run.getAction(PipelineQueueInfoAction.class));
    }

    protected void checkHostNameTag(Map meta) {
        assertNotNull(meta);
        Object nodeName = meta.get(CITags.NODE_NAME);
        assertNotNull(nodeName);
        // if nodeName == master, should be null, otherwise should be none
        Object hostTagVal = meta.get(CITags._DD_HOSTNAME);
        if (DatadogUtilities.isMainNode((String)nodeName)) {
            assertNull(hostTagVal);
        } else {
            assertEquals("none", hostTagVal);
        }
    }
}
