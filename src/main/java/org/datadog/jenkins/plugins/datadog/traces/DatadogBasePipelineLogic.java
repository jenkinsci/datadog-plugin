package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.Run;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;


/**
 * Base class with shared code for DatadogTracePipelineLogic and DatadogWebhookPipelineLogic
 */
public abstract class DatadogBasePipelineLogic {

    protected static final String CI_PROVIDER = "jenkins";
    protected static final String HOSTNAME_NONE = "none";

    public abstract JSONObject toJson(BuildPipelineNode current, Run<?, ?> run) throws IOException, InterruptedException;

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    protected Set<String> getNodeLabels(Run run, BuildPipelineNode current, String nodeName) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
        if (current.getNodeLabels() != null && !current.getNodeLabels().isEmpty()) {
            return current.getNodeLabels();
        } else if (pipelineNodeInfoAction != null && !pipelineNodeInfoAction.getNodeLabels().isEmpty()) {
            return pipelineNodeInfoAction.getNodeLabels();
        }

        if (DatadogUtilities.isMainNode(nodeName)) {
            // executor owner is the master node even if the pipeline contains an "agent" block
            if (run.getExecutor() != null) {
                Set<String> nodeLabels = DatadogUtilities.getNodeLabels(run.getExecutor().getOwner());
                if (!nodeLabels.isEmpty()) {
                    return nodeLabels;
                }
            }

            // If there is no labels and the node name is master,
            // we force the label "master".
            final Set<String> masterLabels = new HashSet<>();
            masterLabels.add("master");
            return masterLabels;
        }

        return Collections.emptySet();
    }

    protected String getNodeName(BuildPipelineNode current, BuildData buildData) {
        if (current.getNodeName() != null) {
            return current.getNodeName();
        }
        return buildData.getNodeName("");
    }

    protected String getNodeHostname(BuildPipelineNode current, BuildData buildData) {
        if (current.getNodeHostname() != null) {
            return current.getNodeHostname();
        }
        return buildData.getHostname("");
    }

    protected String buildOperationName(BuildPipelineNode current) {
        return CI_PROVIDER + "." + current.getType().name().toLowerCase();
    }
}
