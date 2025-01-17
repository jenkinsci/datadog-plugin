package org.datadog.jenkins.plugins.datadog.util.git;

import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidCommitSha;
import static org.datadog.jenkins.plugins.datadog.util.git.GitUtils.isValidRepositoryURL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

public class GitUtilsTest {

    @Test
    public void testFilterSensitiveInfo() {
        Assert.assertEquals("http://hostname.com/repo.git", GitUtils.filterSensitiveInfo("http://hostname.com/repo.git"));
        Assert.assertEquals("http://hostname.com/repo.git", GitUtils.filterSensitiveInfo("http://user@hostname.com/repo.git"));
        Assert.assertEquals("http://hostname.com/repo.git", GitUtils.filterSensitiveInfo("http://user%E2%82%AC@hostname.com/repo.git"));
        Assert.assertEquals("http://hostname.com/repo.git", GitUtils.filterSensitiveInfo("http://user:pwd@hostname.com/repo.git"));
        Assert.assertEquals("git@hostname.com:org/repo.git", GitUtils.filterSensitiveInfo("git@hostname.com:org/repo.git"));
    }

    @Test
    public void testNormalizeBranch() {
        Assert.assertNull(GitUtils.normalizeBranch(null));
        Assert.assertNull(GitUtils.normalizeBranch(""));
        Assert.assertNull(GitUtils.normalizeBranch("tags/v1.0.0"));
        Assert.assertEquals("my-branch", GitUtils.normalizeBranch("my-branch"));
        Assert.assertEquals("my-branch", GitUtils.normalizeBranch("/my-branch"));
        Assert.assertEquals("my-branch", GitUtils.normalizeBranch("origin/my-branch"));
        Assert.assertEquals("my-branch", GitUtils.normalizeBranch("refs/heads/my-branch"));
        Assert.assertEquals("my-branch", GitUtils.normalizeBranch("refs/remotes/origin/my-branch"));
    }

    @Test
    public void testIsValidCommitSha() {
        assertFalse(isValidCommitSha(null));
        assertFalse(isValidCommitSha(""));

        assertFalse(isValidCommitSha("5e784c"));
        assertFalse(isValidCommitSha("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"));
        assertTrue(isValidCommitSha("5e784c0e61f728131f526892142083543e013db9"));

        assertFalse(isValidCommitSha(null));
        assertFalse(isValidCommitSha(""));
        assertFalse(isValidCommitSha("refs/heads/master"));
        assertFalse(isValidCommitSha("my-branch-name"));
        assertFalse(isValidCommitSha("7e67476")); // we only consider/expect full SHAs
        assertFalse(isValidCommitSha("7E67476")); // we only consider/expect full SHAs
        assertTrue(isValidCommitSha("f8d01f9626b324eb206c5544fceaadb459dfd93a"));
        assertTrue(isValidCommitSha("F8D01F9626B324EB206C5544FCEAADB459DFD93A"));
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
