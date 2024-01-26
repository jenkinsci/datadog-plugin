package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

public interface TraceWriteStrategy {
    @Nullable
    Span createSpan(BuildData buildData, Run<?, ?> run);

    @Nonnull
    Collection<Span> createSpan(FlowNode flowNode, Run<?, ?> run);

    void send(Collection<Span> spans);
}
