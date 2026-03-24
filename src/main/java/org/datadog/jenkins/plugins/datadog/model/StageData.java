package org.datadog.jenkins.plugins.datadog.model;

import org.apache.commons.lang.StringEscapeUtils;
import org.datadog.jenkins.plugins.datadog.util.json.ToJson;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Keeps the information of a Stage to calculate the Stage breakdown.
 */
public class StageData implements Serializable, Comparable<StageData>, ToJson {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(StageData.class.getName());

    private final String name;
    private final long startTimeInMicros;
    private final long endTimeInMicros;
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
        return Long.compare(this.startTimeInMicros, other.getStartTimeInMicros());
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

    @Override
    public String toJson() {
        if(name == null || name.isEmpty()){
            logger.fine("Cannot extract StageData as JSON. Stage name is null or empty.");
            return "";
        }

        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\"").append(":").append("\"").append(StringEscapeUtils.escapeJavaScript(name)).append("\"").append(",");
        sb.append("\"duration\"").append(":").append(durationInNanos);
        sb.append("}");
        return sb.toString();
    }
}
