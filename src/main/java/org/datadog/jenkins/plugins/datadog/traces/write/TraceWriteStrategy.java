package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;

public interface TraceWriteStrategy {

    @Nullable
    Payload serialize(BuildData buildData, Run<?, ?> run);

    @Nullable
    Payload serialize(PipelineStepData stepData, Run<?, ?> run) throws IOException, InterruptedException;

    void send(Collection<Payload> spans);

    default void close() {}
}
