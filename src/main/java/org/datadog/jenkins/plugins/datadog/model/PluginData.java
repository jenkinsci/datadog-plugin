package org.datadog.jenkins.plugins.datadog.model;

import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;

public class PluginData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private long count = 0;
    private long failed = 0;
    private long active = 0;
    private long inactive = 0;
    private long updatable = 0;

    @Nullable
    private Integer warnings;

    private PluginData(Builder builder) {
        this.count = builder.count;
        this.failed = builder.failed;
        this.active = builder.active;
        this.inactive = builder.inactive;
        this.updatable = builder.updatable;
        this.warnings = builder.warnings;
    }

    public static PluginData.Builder newBuilder() {
        return new PluginData.Builder();
    }

    public static class Builder {
        private long count = 0;
        private long failed = 0;
        private long active = 0;
        private long inactive = 0;
        private long updatable = 0;

        @Nullable
        private Integer warnings;

        private Builder() {
        }

        public Builder withCount(long count) {
            this.count = count;
            return this;
        }

        public Builder withFailed(long failed) {
            this.failed = failed;
            return this;
        }

        public Builder withWarnings(Integer warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder withActive(long active) {
            this.active = active;
            return this;
        }

        public Builder incrementActive() {
            this.active++;
            return this;
        }

        public Builder withInactive(long inactive) {
            this.inactive = inactive;
            return this;
        }

        public Builder incrementInactive() {
            this.inactive++;
            return this;
        }

        public Builder withUpdatable(long updatable) {
            this.updatable = updatable;
            return this;
        }

        public Builder incrementUpdatable() {
            this.updatable++;
            return this;
        }

        public PluginData build() {
            return new PluginData(this);
        }
    }

    public long getCount() {
        return count;
    }

    public long getFailed() {
        return failed;
    }

    public long getActive() {
        return active;
    }

    public long getInactive() {
        return inactive;
    }

    public long getUpdatable() {
        return updatable;
    }

    @Nullable
    public Integer getWarnings() {
        return warnings;
    }
}
