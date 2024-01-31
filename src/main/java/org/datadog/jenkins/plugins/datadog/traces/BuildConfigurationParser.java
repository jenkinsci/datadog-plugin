package org.datadog.jenkins.plugins.datadog.traces;


import com.google.common.collect.Maps;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.Job;
import hudson.model.Run;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.datadog.jenkins.plugins.datadog.listeners.DatadogBuildListener;

public class BuildConfigurationParser {

    private static final Logger logger = Logger.getLogger(DatadogBuildListener.class.getName());

    /**
     * Extract the configuration values from a {@link MatrixProject} run ({@see https://plugins.jenkins.io/matrix-project/}).
     * <p>
     * It is possible to create a "Multi-configuration" project in Jenkins.
     * For these projects a matrix of build parameters can be configured.
     * When such project is triggered, a separate build is started for each configuration matrix cell (that is, each combination of parameters).
     * Configuration matrix parameters used for a specific build are available in the full name of the build's parent (an instance of {@link MatrixConfiguration}),
     * for example: {@code Jenkins job name: jobName/KEY1=VALUE1,KEY2=VALUE2}.
     * This method helps to parse these configuration parameters to a map.
     * <p>
     * If the run is not a matrix project execution, an empty map is returned.
     */
    @Nonnull
    public static Map<String, String> parseConfigurations(@Nonnull Run<?, ?> run) {
        Job<?, ?> job = run.getParent();
        if (!(job instanceof MatrixConfiguration)) {
            return Collections.emptyMap();
        }

        String jobName = job.getFullName();
        if(jobName == null || !jobName.contains("/")) {
            return Collections.emptyMap();
        }

        String configurationString = jobName.substring(jobName.indexOf("/") + 1).toLowerCase().trim();
        final String[] configsKeyValue = configurationString.split(",");
        final Map<String, String> configurations = Maps.newHashMapWithExpectedSize(configsKeyValue.length);
        for(final String configKeyValue : configsKeyValue) {
            final String[] keyValue = configKeyValue.trim().split("=");
            if (keyValue.length == 2) {
                configurations.put(keyValue[0], keyValue[1]);
            } else {
                logger.fine("Malformed configuration pair " + Arrays.toString(keyValue) + " in job " + jobName);
            }
        }
        return configurations;
    }

}
