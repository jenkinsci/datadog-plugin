package org.datadog.jenkins.plugins.datadog.util.git;

import java.io.Serializable;
import javax.annotation.Nullable;

public class RepositoryInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String repoUrl;
    private final String defaultBranch;
    private final String branch;

    public RepositoryInfo(String repoUrl, String defaultBranch, String branch) {
        this.repoUrl = repoUrl;
        this.defaultBranch = defaultBranch;
        this.branch = branch;
    }

    @Nullable
    public String getRepoUrl() {
        return repoUrl;
    }

    @Nullable
    public String getDefaultBranch() {
        return defaultBranch;
    }

    @Nullable
    public String getBranch() {
        return branch;
    }
}
