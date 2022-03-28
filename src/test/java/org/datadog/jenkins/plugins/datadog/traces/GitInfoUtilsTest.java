package org.datadog.jenkins.plugins.datadog.traces;

import org.junit.Assert;
import org.junit.Test;

public class GitInfoUtilsTest {

    @Test
    public void testFilterSensitiveInfo() {
        Assert.assertEquals("http://hostname.com/repo.git", GitInfoUtils.filterSensitiveInfo("http://hostname.com/repo.git"));
        Assert.assertEquals("http://hostname.com/repo.git", GitInfoUtils.filterSensitiveInfo("http://user@hostname.com/repo.git"));
        Assert.assertEquals("http://hostname.com/repo.git", GitInfoUtils.filterSensitiveInfo("http://user%E2%82%AC@hostname.com/repo.git"));
        Assert.assertEquals("http://hostname.com/repo.git", GitInfoUtils.filterSensitiveInfo("http://user:pwd@hostname.com/repo.git"));
        Assert.assertEquals("git@hostname.com:org/repo.git", GitInfoUtils.filterSensitiveInfo("git@hostname.com:org/repo.git"));
    }
}