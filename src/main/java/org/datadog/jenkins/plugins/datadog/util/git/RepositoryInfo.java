package org.datadog.jenkins.plugins.datadog.util.git;

import java.io.Serializable;

public class RepositoryInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String defaultBranch;

    public RepositoryInfo(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }
}
