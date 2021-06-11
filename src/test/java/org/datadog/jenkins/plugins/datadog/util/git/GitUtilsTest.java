package org.datadog.jenkins.plugins.datadog.util.git;

import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GitUtilsTest {

    @Test
    public void testIsValidCommit() {
        assertFalse(isValidCommit(null));
        assertFalse(isValidCommit(""));

        assertFalse(isValidCommit("5e784c"));
        assertFalse(isValidCommit("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"));
        assertTrue(isValidCommit("5e784c0e61f728131f526892142083543e013db9"));
    }

}