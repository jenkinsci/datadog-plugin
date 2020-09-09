package org.datadog.jenkins.plugins.datadog.traces;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper for the JobName to extract the job name and configurations for traces.
 *
 * Example1: (Freestyle Project)
 * - Jenkins job name: jobName
 * -- TraceJobName: jobName
 * -- Configurations: EMPTY
 *
 * Example2: (Multibranch Pipeline)
 * - Jenkins job name: jobName/master
 * -- TraceJobName: jobName
 * -- Configurations: EMPTY
 *
 * Example3: (Multiconfigurations project)
 * - Jenkins job name: jobName/KEY1=VALUE1,KEY2=VALUE2/master
 * -- TraceJobName: jobName
 * -- Configurations: map(key1=valu2, key2=value2)
 */
public class JobNameWrapper {

    private final String traceJobName;
    private final Map<String, String> configurations = new HashMap<>();

    public JobNameWrapper(final String rawJobName, final String gitBranch) {
        if(rawJobName == null) {
            this.traceJobName = null;
            return;
        }

        // First, the git branch is removed from the raw jobName
        final String jobNameNoBranch;
        if(gitBranch != null && !gitBranch.isEmpty()) {
            jobNameNoBranch = rawJobName.trim().replace("/" + gitBranch, "");
        } else {
            jobNameNoBranch = rawJobName;
        }

        // Once the branch has been removed, we try to extract
        // the configurations from the job name.
        // The configurations have the form like "key1=value1,key2=value2"
        final String[] jobNameParts = jobNameNoBranch.split("/");
        if(jobNameParts.length > 1 && jobNameParts[1].contains("=")){
            final String configsStr = jobNameParts[1].toLowerCase().trim();
            final String[] configsKeyValue = configsStr.split(",");
            for(final String configKeyValue : configsKeyValue) {
                final String[] keyValue = configKeyValue.trim().split("=");
                configurations.put(keyValue[0], keyValue[1]);
            }
        }

        if(configurations.isEmpty()) {
            //If there is no configurations,
            //the jobName is the original one without branch.
            this.traceJobName = jobNameNoBranch;
        } else {
            //If there are configurations,
            //the jobName is the first part of the splited raw jobName.
            this.traceJobName = jobNameParts[0];
        }
    }

    public String getTraceJobName() {
        return traceJobName;
    }

    public Map<String, String> getConfigurations() {
        return configurations;
    }
}
