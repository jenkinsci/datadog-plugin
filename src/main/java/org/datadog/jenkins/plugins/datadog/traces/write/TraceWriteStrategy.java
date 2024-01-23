package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;

public interface TraceWriteStrategy {
    @Nullable
    JSONObject serialize(BuildData buildData, Run<?, ?> run);

    @Nonnull
    JSONObject serialize(BuildPipelineNode node, Run<?, ?> run) throws IOException, InterruptedException;

    void send(List<JSONObject> spans);
}
