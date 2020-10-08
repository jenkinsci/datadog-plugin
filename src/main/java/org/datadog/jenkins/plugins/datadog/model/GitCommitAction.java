package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

import java.io.Serializable;

/**
 * Keeps the Git commit related information.
 */
public class GitCommitAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String commit;
    private final String message;
    private final String authorName;
    private final String authorEmail;
    private final String authorDate;
    private final String committerName;
    private final String committerEmail;
    private final String committerDate;

    private GitCommitAction(Builder builder) {
        this.commit = builder.commit;
        this.message = builder.message;
        this.authorName = builder.authorName;
        this.authorEmail = builder.authorEmail;
        this.authorDate = builder.authorDate;
        this.committerName = builder.committerName;
        this.committerEmail = builder.committerEmail;
        this.committerDate = builder.committerDate;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String commit;
        private String message;
        private String authorName;
        private String authorEmail;
        private String authorDate;
        private String committerName;
        private String committerEmail;
        private String committerDate;

        private Builder(){}

        public Builder withCommit(final String commit) {
            this.commit = commit;
            return this;
        }

        public Builder withMessage(final String message) {
            this.message = message;
            return this;
        }

        public Builder withAuthorName(final String authorName) {
            this.authorName = authorName;
            return this;
        }

        public Builder withAuthorEmail(final String authorEmail) {
            this.authorEmail = authorEmail;
            return this;
        }

        public Builder withAuthorDate(final String authorDate) {
            this.authorDate = authorDate;
            return this;
        }

        public Builder withCommitterName(final String committerName){
            this.committerName = committerName;
            return this;
        }

        public Builder withCommitterEmail(final String committerEmail) {
            this.committerEmail = committerEmail;
            return this;
        }

        public Builder withCommitterDate(final String committerDate) {
            this.committerDate = committerDate;
            return this;
        }

        public GitCommitAction build() {
            return new GitCommitAction(this);
        }
    }

    public String getCommit() {
        return commit;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public String getAuthorDate() {
        return authorDate;
    }

    public String getCommitterName() {
        return committerName;
    }

    public String getCommitterEmail() {
        return committerEmail;
    }

    public String getCommitterDate() {
        return committerDate;
    }
}
