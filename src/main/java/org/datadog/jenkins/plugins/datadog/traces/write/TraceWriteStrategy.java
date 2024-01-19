package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.util.Collection;
import java.util.List;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

public interface TraceWriteStrategy {
    JSONObject serialize(BuildData buildData, Run<?, ?> run);

    Collection<JSONObject> serialize(FlowNode flowNode, Run<?, ?> run);

    void send(List<JSONObject> spans);
}
