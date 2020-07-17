package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represent a stage of the Jenkins Pipeline.
 */
public class BuildStage {

    private final BuildStageKey key;
    private final List<BuildStage> children;
    private BuildData data;

    private Long startTime;
    private Long endTime;
    private String result;
    private boolean error;

    public static BuildStageBuilder buildStage(final BuildStageKey key) {
        return new BuildStageBuilder(key);
    }

    public static BuildStageBuilder buildStage(final String stageId, final String stageName) {
        return new BuildStageBuilder(stageId, stageName);
    }

    public static BuildStageKey buildStageKey(final String stageId, final String stageName) {
        return new BuildStageKey(stageId, stageName);
    }

    public BuildStage(final BuildStageBuilder builder) {
        this.key = builder.key;
        this.children = new ArrayList<>();

        if(builder.data != null) {
            this.data = builder.data;
        }

        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.result = builder.result;
        this.error = builder.error;
    }

    public void addChild(final BuildStage child) {
        children.add(child);
    }

    public BuildStageKey getKey() {
        return key;
    }

    public String getId() {
        return key.id;
    }

    public String getName() {
        return key.name;
    }

    public BuildData getData() {
        return data;
    }

    public Long getStartTime() {
        return startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public String getResult() {
        return result;
    }

    public boolean isError() {
        return error;
    }

    public List<BuildStage> getChildren() {
        return children;
    }

    public BuildStage getChild(final BuildStageKey id) {
        if(children.isEmpty()) {
            return null;
        }

        for(final BuildStage child : children) {
            if(id.equals(child.getKey())){
                return child;
            }
        }

        return null;
    }

    public void updateData(final BuildStage stage) {
        this.data = stage.data;
        this.startTime = stage.startTime;
        this.endTime = stage.endTime;
        this.result = stage.result;
        this.error = stage.error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildStage that = (BuildStage) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BuildStage{");
        sb.append("key=").append(key);
        sb.append(", children=").append(children);
        sb.append(", data=").append(data);
        sb.append(", startTime=").append(startTime);
        sb.append(", endTime=").append(endTime);
        sb.append(", result='").append(result).append('\'');
        sb.append(", error=").append(error);
        sb.append('}');
        return sb.toString();
    }

    public static class BuildStageBuilder {
        private final BuildStageKey key;
        private BuildData data;
        private Long startTime;
        private Long endTime;
        private String result;
        private boolean error;

        public BuildStageBuilder(String stageId, String stageName) {
            this.key = new BuildStageKey(stageId, stageName);
        }

        public BuildStageBuilder(final BuildStageKey key) {
            this.key = key;
        }

        public BuildStageBuilder withData(final BuildData data) {
            this.data = data;
            return this;
        }

        public BuildStageBuilder withStartTime(final Long startTime) {
            this.startTime = startTime;
            return this;
        }

        public BuildStageBuilder withEndTime(final Long endTime) {
            this.endTime = endTime;
            return this;
        }

        public BuildStageBuilder withResult(final String result) {
            this.result = result;
            if(Result.FAILURE.toString().equals(this.result)){
                this.error = true;
            }
            return this;
        }

        public BuildStage build() {
            return new BuildStage(this);
        }
    }


    public static class BuildStageKey {
        private final String id;
        private final String name;

        private BuildStageKey(final String stageId, final String stageName) {
            this.id = stageId;
            this.name = stageName;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BuildStageKey that = (BuildStageKey) o;
            return Objects.equals(id, that.id) &&
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("BuildStageKey{");
            sb.append("id='").append(id).append('\'');
            sb.append(", name='").append(name).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}
