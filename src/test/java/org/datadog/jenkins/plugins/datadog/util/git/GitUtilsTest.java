package org.datadog.jenkins.plugins.datadog.util.git;

import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommit;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidRepositoryURL;
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

    @Test
    public void testIsValidRepositoryUrl() {
        assertFalse(isValidRepositoryURL(null));
        assertFalse(isValidRepositoryURL(""));
        assertFalse(isValidRepositoryURL("testUrl"));

        assertTrue(isValidRepositoryURL("ssh://user@host.xz:1234/path/to/repo.git"));
        assertTrue(isValidRepositoryURL("git://host.xz:1234/path/to/repo.git"));
        assertTrue(isValidRepositoryURL("https://host.xz:1234/path/to/repo.git"));
        assertTrue(isValidRepositoryURL("ftp://host.xz:1234/path/to/repo.git"));
        assertTrue(isValidRepositoryURL("user@host.xz:path/to/repo.git"));
        assertTrue(isValidRepositoryURL("ssh://user@host.xz:1234/~user/path/to/repo.git"));
        assertTrue(isValidRepositoryURL("git://host.xz:1234/~user/path/to/repo.git"));
        assertTrue(isValidRepositoryURL("https://gitlab.abc.xyz.com/org/repos/foo/bar.git"));
        assertTrue(isValidRepositoryURL("https://github-ci-token:MYTOKEN@github.com/org/repo.git"));
        assertTrue(isValidRepositoryURL("ssh://foo.bar.net:7999/org/repo.git"));
        assertTrue(isValidRepositoryURL("https://git.foo.com/org.org/repo-repo.git"));
        assertTrue(isValidRepositoryURL("org-org@github.com:org/repo.git"));
        assertTrue(isValidRepositoryURL("org_example@github.com:org/some-repo_example.git"));
    }

}