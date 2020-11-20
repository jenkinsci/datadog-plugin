package org.datadog.jenkins.plugins.datadog.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Objects;

/**
 * Keeps the information of a Stage to calculate the Stage breakdown.
 */
public class StageData implements Serializable, Comparable<StageData> {

    private static final long serialVersionUID = 1L;

    @Expose
    private final String name;
    @Expose(serialize = false)
    private final long startTimeInMicros;
    @Expose(serialize = false)
    private final long endTimeInMicros;
    @Expose
    @SerializedName("duration")
    private final long durationInNanos;

    private StageData(final Builder builder) {
        this.name = builder.name;
        this.startTimeInMicros = builder.start;
        this.endTimeInMicros = builder.end;
        this.durationInNanos = (this.endTimeInMicros - this.startTimeInMicros) * 1000;
    }

    public String getName() {
        return name;
    }

    public long getStartTimeInMicros() {
        return startTimeInMicros;
    }

    public long getEndTimeInMicros() {
        return endTimeInMicros;
    }

    public long getDurationInNanos() {
        return durationInNanos;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int compareTo(StageData other) {
        if(this.startTimeInMicros < other.getStartTimeInMicros()) {
            return -1;
        } else if(this.startTimeInMicros > other.getStartTimeInMicros()) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StageData stageData = (StageData) o;
        return startTimeInMicros == stageData.startTimeInMicros;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTimeInMicros);
    }

    public static class Builder {
        private String name;
        private long start;
        private long end;

        public Builder withName(final String name) {
            this.name = name;
            return this;
        }

        public Builder withStartTimeInMicros(final long start){
            this.start = start;
            return this;
        }

        public Builder withEndTimeInMicros(final long end) {
            this.end = end;
            return this;
        }

        public StageData build() {
            return new StageData(this);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StageData{");
        sb.append("name='").append(name).append('\'');
        sb.append(", startTimeInMicros=").append(startTimeInMicros);
        sb.append(", endTimeInMicros=").append(endTimeInMicros);
        sb.append(", durationInMicros=").append(durationInNanos);
        sb.append('}');
        return sb.toString();
    }
}
