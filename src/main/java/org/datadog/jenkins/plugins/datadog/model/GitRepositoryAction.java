package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

import java.io.Serializable;

/**
 * Keeps the Git repository related information.
 */
public class GitRepositoryAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String repositoryURL;
    private final String defaultBranch;

    private GitRepositoryAction(final Builder builder) {
        this.repositoryURL = builder.repositoryURL;
        this.defaultBranch = builder.defaultBranch;
    }

    public String getRepositoryURL(){
        return repositoryURL;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String repositoryURL;
        private String defaultBranch;

        private Builder(){}

        public Builder withRepositoryURL(final String repositoryURL) {
            this.repositoryURL = repositoryURL;
            return this;
        }

        public Builder withDefaultBranch(final String defaultBranch) {
            this.defaultBranch = defaultBranch;
            return this;
        }

        public GitRepositoryAction build(){
            return new GitRepositoryAction(this);
        }
    }
}
