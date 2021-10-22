package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.model.CIGlobalTagsAction;
import org.datadog.jenkins.plugins.datadog.model.GitCommitAction;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineQueueInfoAction;
import org.datadog.jenkins.plugins.datadog.model.StageBreakdownAction;
import org.datadog.jenkins.plugins.datadog.traces.BuildSpanAction;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.datadog.jenkins.plugins.datadog.traces.IsPipelineAction;
import org.datadog.jenkins.plugins.datadog.traces.StepDataAction;
import org.datadog.jenkins.plugins.datadog.traces.StepTraceDataAction;
import org.datadog.jenkins.plugins.datadog.traces.message.TraceSpan;

import java.util.Map;

public abstract class DatadogTraceAbstractTest {

    protected void assertGitVariables(TraceSpan span, String defaultBranch) {
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
        assertEquals("https://github.com/johndoe/foobar.git", meta.get(CITags.GIT_REPOSITORY_URL));
        assertEquals(defaultBranch, meta.get(CITags.GIT_DEFAULT_BRANCH));
    }

    protected void assertCleanupActions(Run<?,?> run) {
        assertNull(run.getAction(BuildSpanAction.class));
        assertNull(run.getAction(StepDataAction.class));
        assertNull(run.getAction(CIGlobalTagsAction.class));
        assertNull(run.getAction(GitCommitAction.class));
        assertNull(run.getAction(GitRepositoryAction.class));
        assertNull(run.getAction(PipelineNodeInfoAction.class));
        assertNull(run.getAction(PipelineQueueInfoAction.class));
        assertNull(run.getAction(StageBreakdownAction.class));
        assertNull(run.getAction(IsPipelineAction.class));
        assertNull(run.getAction(StepTraceDataAction.class));
    }
}
