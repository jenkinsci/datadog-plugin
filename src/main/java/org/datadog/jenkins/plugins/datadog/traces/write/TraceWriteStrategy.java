package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.io.IOException;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;

public interface TraceWriteStrategy {
    @Nullable
    Payload serialize(BuildData buildData, Run<?, ?> run);

    @Nonnull
    Payload serialize(PipelineStepData stepData, Run<?, ?> run) throws IOException, InterruptedException;

    void send(Collection<Payload> spans);
}
