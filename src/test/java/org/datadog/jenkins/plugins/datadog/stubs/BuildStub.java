package org.datadog.jenkins.plugins.datadog.stubs;

import hudson.EnvVars;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;
import edu.umd.cs.findbugs.annotations.NonNull;

public class BuildStub extends Build<ProjectStub, BuildStub> {

    private Result result;
    private EnvVars envVars;
    private BuildStub previousSuccessfulBuild;
    private long duration;
    private int number;
    private BuildStub previousBuiltBuild;
    private BuildStub previousNotFailedBuild;

    public BuildStub(@NonNull ProjectStub project, Result result, EnvVars envVars, BuildStub previousSuccessfulBuild,
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

    /**
     * Overridden to avoid calling {@link jenkins.model.TransientActionFactory#factoriesFor}, which since Jenkins
     * 2.555 requires a fully initialized Jenkins instance (it looks up a singleton extension), and therefore
     * throws in these plain unit tests that only stub out {@link jenkins.model.Jenkins}.
     */
    @Override
    public <T extends Action> List<T> getActions(Class<T> type) {
        return Util.filter(getActions(), type);
    }

    @Override
    public <T extends Action> T getAction(Class<T> type) {
        for (Action action : getActions()) {
            if (type.isInstance(action)) {
                return type.cast(action);
            }
        }
        return null;
    }

    protected BuildStub(@NonNull ProjectStub project) throws IOException {
        super(project);
    }

    public void run() {
        // no-op
    }

    public Result getResult() {
        return this.result;
    }

    @NonNull
    public EnvVars getEnvironment(@NonNull TaskListener listener) throws IOException, InterruptedException {
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

    @NonNull
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