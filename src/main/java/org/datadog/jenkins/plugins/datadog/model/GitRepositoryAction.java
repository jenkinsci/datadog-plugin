package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

import java.io.Serializable;

/**
 * Keeps the Git repository related information.
 */
public class GitRepositoryAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String defaultBranch;

    private GitRepositoryAction(final Builder builder) {
        this.defaultBranch = builder.defaultBranch;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String defaultBranch;

        private Builder(){}

        public Builder withDefaultBranch(final String defaultBranch) {
            this.defaultBranch = defaultBranch;
            return this;
        }

        public GitRepositoryAction build(){
            return new GitRepositoryAction(this);
        }
    }
}
