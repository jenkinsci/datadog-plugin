package org.datadog.jenkins.plugins.datadog.model;

import static org.junit.Assert.assertEquals;

import org.datadog.jenkins.plugins.datadog.model.git.GitMetadata;
import org.datadog.jenkins.plugins.datadog.model.git.Source;
import org.junit.Test;

public class GitMetadataActionTest {

    @Test
    public void nullMetadataIsIgnored() {
        GitMetadataAction action = new GitMetadataAction();
        action.addMetadata(Source.GIT_CLIENT, null);
        assertEquals(GitMetadata.EMPTY, action.getMetadata());
    }

    @Test
    public void metadataWithoutCommitDoesNotBreakGetMetadata() {
        GitMetadataAction action = new GitMetadataAction();
        action.addMetadata(Source.GIT_CLIENT, new GitMetadata.Builder().branch("main").commitMetadata(null).build());
        assertEquals("main", action.getMetadata().getBranch());
    }
}
