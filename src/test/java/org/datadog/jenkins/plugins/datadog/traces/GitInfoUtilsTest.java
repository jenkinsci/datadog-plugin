package org.datadog.jenkins.plugins.datadog.traces;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitInfoUtilsTest {

    @Test
    public void testFilterSensitiveInfo() {
        Assert.assertEquals("http://hostname.com/repo.git", GitInfoUtils.filterSensitiveInfo("http://hostname.com/repo.git"));
        Assert.assertEquals("http://hostname.com/repo.git", GitInfoUtils.filterSensitiveInfo("http://user@hostname.com/repo.git"));
        Assert.assertEquals("http://hostname.com/repo.git", GitInfoUtils.filterSensitiveInfo("http://user%E2%82%AC@hostname.com/repo.git"));
        Assert.assertEquals("http://hostname.com/repo.git", GitInfoUtils.filterSensitiveInfo("http://user:pwd@hostname.com/repo.git"));
        Assert.assertEquals("git@hostname.com:org/repo.git", GitInfoUtils.filterSensitiveInfo("git@hostname.com:org/repo.git"));
    }

    @Test
    public void testIsSha() {
        assertFalse(GitInfoUtils.isSha(null));
        assertFalse(GitInfoUtils.isSha(""));
        assertFalse(GitInfoUtils.isSha("refs/heads/master"));
        assertFalse(GitInfoUtils.isSha("my-branch-name"));
        assertFalse(GitInfoUtils.isSha("7e67476")); // we only consider/expect full SHAs
        assertFalse(GitInfoUtils.isSha("7E67476")); // we only consider/expect full SHAs
        assertTrue(GitInfoUtils.isSha("f8d01f9626b324eb206c5544fceaadb459dfd93a"));
        assertTrue(GitInfoUtils.isSha("F8D01F9626B324EB206C5544FCEAADB459DFD93A"));
    }
}