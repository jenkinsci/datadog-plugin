package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.Run;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;


/**
 * Base class with shared code for DatadogTracePipelineLogic and DatadogWebhookPipelineLogic
 */
public abstract class DatadogBasePipelineLogic {

    protected static final String CI_PROVIDER = "jenkins";
    protected static final String HOSTNAME_NONE = "none";

    @Nonnull
    public abstract JSONObject toJson(PipelineStepData current, Run<?, ?> run) throws IOException, InterruptedException;

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    protected Set<String> getNodeLabels(Run run, PipelineStepData current, String nodeName) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
        if (current.getNodeLabels() != null && !current.getNodeLabels().isEmpty()) {
            // First examine if current step has info about the node it was executed on.
            return current.getNodeLabels();

        } else if (pipelineNodeInfoAction != null && !pipelineNodeInfoAction.getNodeLabels().isEmpty()) {
            // Examine PipelineNodeInfoAction associated with the pipeline.
            // The action is populated in step listener based on environment and executor data available for pipeline steps.
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

    protected String getNodeName(PipelineStepData current, BuildData buildData) {
        if (current.getNodeName() != null) {
            return current.getNodeName();
        }
        // It seems like "built-in" node as the default value does not have much practical sense.
        // It is done to preserve existing behavior (note that this logic is not applied to metrics - also to preserve the plugin's existing behavior).
        // The mechanism before the changes was the following:
        // - DatadogBuildListener#onInitialize created a BuildData instance
        // - that BuildData had its nodeName populated from environment variables obtained from Run
        // - the instance was persisted in an Action attached to Run, and was used to populate the node name of the pipeline span (always as the last fallback)
        // For pipelines, the environment variables that Run#getEnvironment returns at the beginning of the run always (!) contain NODE_NAME = "built-in" (when invoked at the end of the run, the env will have a different set of variables).
        // This is true regardless of whether the pipeline definition has a top-level agent block or not.
        // For freestyle projects the correct NODE_NAME seems to be available in the run's environment variables at every stage of the build's lifecycle.
        return buildData.getNodeName("built-in");
    }

    protected String getNodeHostname(PipelineStepData current, BuildData buildData) {
        if (current.getNodeHostname() != null) {
            return current.getNodeHostname();
        }
        return buildData.getHostname("");
    }

    protected String buildOperationName(PipelineStepData current) {
        return CI_PROVIDER + "." + current.getType().name().toLowerCase();
    }
}
