package org.datadog.jenkins.plugins.datadog.stubs;

import hudson.EnvVars;
import hudson.model.Build;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.IOException;
import javax.annotation.Nonnull;

public class BuildStub extends Build<ProjectStub, BuildStub> {

    private Result result;
    private EnvVars envVars;
    private BuildStub previousSuccessfulBuild;
    private long duration;
    private int number;
    private BuildStub previousBuiltBuild;
    private BuildStub previousNotFailedBuild;

    public BuildStub(@Nonnull ProjectStub project, Result result, EnvVars envVars, BuildStub previousSuccessfulBuild,
                     long duration, int number, BuildStub previousBuiltBuild, long timestamp, BuildStub previousNotFailedBuild)
            throws IOException {
        this(project);
        this.result = result;
        this.envVars = envVars != null ? envVars : new EnvVars();
        this.previousSuccessfulBuild = previousSuccessfulBuild;
        this.duration = duration;
        this.number = number;
        this.previousBuiltBuild = previousBuiltBuild;
        this.timestamp = timestamp;
        this.previousNotFailedBuild = previousNotFailedBuild;
    }

    @Override
    public Node getBuiltOn() {
        return null;
    }

    protected BuildStub(@Nonnull ProjectStub project) throws IOException {
        super(project);
    }

    public void run() {
        // no-op
    }

    public Result getResult() {
        return this.result;
    }

    @Nonnull
    public EnvVars getEnvironment(@Nonnull TaskListener listener) throws IOException, InterruptedException {
        return this.envVars;
    }

    public BuildStub getPreviousSuccessfulBuild() {
        return this.previousSuccessfulBuild;
    }

    public long getDuration() {
        return this.duration;
    }

    public int getNumber() {
        return this.number;
    }

    @Nonnull
    public ProjectStub getParent() {
        return project;
    }

    public BuildStub getPreviousBuiltBuild() {
        return this.previousBuiltBuild;
    }

    public BuildStub getPreviousNotFailedBuild() {
        return this.previousNotFailedBuild;
    }

    public long getQueueId() {
        return 1L;
    }
}