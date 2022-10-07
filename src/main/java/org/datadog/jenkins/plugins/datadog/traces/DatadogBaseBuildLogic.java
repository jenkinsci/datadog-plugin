package org.datadog.jenkins.plugins.datadog.traces;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineNodeInfoAction;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;

import hudson.model.Run;

/**
 * Base class for DatadogTraceBuildLogic and DatadogPipelineBuildLogic
 */
public class DatadogBaseBuildLogic {

    protected static final String HOSTNAME_NONE = "none";
    private static final Logger logger = Logger.getLogger(DatadogBaseBuildLogic.class.getName());


    protected String getNodeName(Run<?, ?> run, BuildData buildData, BuildData updatedBuildData) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
        if(pipelineNodeInfoAction != null){
            return pipelineNodeInfoAction.getNodeName();
        }

        return buildData.getNodeName("").isEmpty() ? updatedBuildData.getNodeName("") : buildData.getNodeName("");
    }

    protected String getNodeHostname(Run<?, ?> run, BuildData updatedBuildData) {
        final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
        if(pipelineNodeInfoAction != null){
            return pipelineNodeInfoAction.getNodeHostname();
        } else if (!updatedBuildData.getHostname("").isEmpty()) {
            return updatedBuildData.getHostname("");
        }
        return null;
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    protected Set<String> getNodeLabels(Run<?,?> run, final String nodeName) {
        try {
            if(run == null){
                return Collections.emptySet();
            }

            final PipelineNodeInfoAction pipelineNodeInfoAction = run.getAction(PipelineNodeInfoAction.class);
            if(pipelineNodeInfoAction != null) {
                return pipelineNodeInfoAction.getNodeLabels();
            }

            if(run.getExecutor() != null && run.getExecutor().getOwner() != null) {
                Set<String> nodeLabels = DatadogUtilities.getNodeLabels(run.getExecutor().getOwner());
                if(nodeLabels != null && !nodeLabels.isEmpty()) {
                    return nodeLabels;
                }
            }

            // If there is no labels and the node name is master,
            // we force the label "master".
            if("master".equalsIgnoreCase(nodeName)){
                final Set<String> masterLabels = new HashSet<>();
                masterLabels.add("master");
                return masterLabels;
            }

            return Collections.emptySet();
        } catch (Exception ex) {
            logger.fine("Unable to find node labels: " + ex.getMessage());
            return Collections.emptySet();
        }
    }

    protected long getMillisInQueue(BuildData buildData) {
        // Reported by the Jenkins Queue API.
        // It's not included in the root span duration.
        final long millisInQueue = buildData.getMillisInQueue(-1L);

        // Reported by a child span.
        // It's included in the root span duration.
        final long propagatedMillisInQueue = buildData.getPropagatedMillisInQueue(-1L);
        return Math.max(Math.max(millisInQueue, propagatedMillisInQueue), 0);
    }

}