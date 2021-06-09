package org.datadog.jenkins.plugins.datadog.model;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.labels.LabelAtom;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class StepData implements Serializable {

    private static final long serialVersionUID = 1L;

    private static transient final Logger logger = Logger.getLogger(StepData.class.getName());

    private final Map<String, String> envVars;
    private final String nodeName;
    private final String nodeHostname;
    private final String workspace;
    private final Set<String> nodeLabels;

    public StepData(final StepContext stepContext){
        this.envVars = getEnvVars(stepContext);
        this.nodeName = getNodeName(stepContext);
        this.nodeHostname = getNodeHostname(stepContext);
        this.workspace = getNodeWorkspace(stepContext);
        this.nodeLabels = getNodeLabels(stepContext);
    }

    public Map<String, String> getEnvVars() {
        return envVars;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getNodeHostname() {
        return nodeHostname;
    }

    public String getWorkspace() {
        return workspace;
    }

    public Set<String> getNodeLabels() {
        return nodeLabels;
    }


    /**
     * Returns the workspace filepath of the remote node which is executing a determined {@code Step}
     * @param stepContext
     * @return absolute filepath of the workspace of the remote node.
     */
    private String getNodeWorkspace(final StepContext stepContext) {
        FilePath filePath = null;
        try {
            filePath = stepContext.get(FilePath.class);
        } catch (Exception e){
            logger.fine("Unable to extract FilePath information of the StepContext.");
        }

        if(filePath == null) {
            return null;
        }

        return filePath.getRemote();
    }

    /**
     * Returns the hostname of the remote node which is executing a determined {@code Step}
     * See {@code Computer.getHostName()}
     * @param stepContext
     * @return hostname of the remote node.
     */
    private String getNodeHostname(final StepContext stepContext) {
        try {
            Computer computer = stepContext.get(Computer.class);
            if(computer == null) {
                return null;
            }

            return computer.getHostName();
        } catch (Exception e){
            logger.fine("Unable to extract hostname from StepContext.");
            return null;
        }
    }


    /**
     * Returns the nodeName of the remote node which is executing a determined {@code Step}
     * @param stepContext
     * @return node name of the remote node.
     */
    private String getNodeName(StepContext stepContext) {
        try {
            Computer computer = stepContext.get(Computer.class);
            return DatadogUtilities.getNodeName(computer);
        } catch (Exception e){
            logger.fine("Unable to extract the node name from StepContext.");
            return null;
        }
    }


    /**
     * Returns the nodeLabels of the remote node which is executing a determined {@code Step}
     * @param stepContext
     * @return node labels of the remote node.
     */
    private Set<String> getNodeLabels(StepContext stepContext) {
        try {
            Computer computer = stepContext.get(Computer.class);
            return DatadogUtilities.getNodeLabels(computer);
        } catch (Exception e) {
            logger.fine("Unable to extract the node labels from StepContext.");
            return Collections.emptySet();
        }
    }


    /**
     * Returns {@code Map<String,String>} with environment variables of a certain {@code StepContext}
     * @param stepContext
     * @return map with environment variables of a stepContext.
     */
    private Map<String, String> getEnvVars(StepContext stepContext) {
        EnvVars envVarsObj = null;
        try {
            envVarsObj = stepContext.get(EnvVars.class);
        } catch (Exception e){
            logger.fine("Unable to extract environment variables from StepContext.");
        }

        if(envVarsObj == null) {
            return Collections.emptyMap();
        }

        return envVarsObj.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
