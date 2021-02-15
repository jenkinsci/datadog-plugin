package org.datadog.jenkins.plugins.datadog.traces;

import org.apache.http.client.utils.URLEncodedUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    public JobNameWrapper(final String jobName, final String gitBranch) {
        if(jobName == null) {
            this.traceJobName = null;
            return;
        }

        final String rawJobName = jobName.trim();
        String jobNameNoBranch = rawJobName;
        // First, the git branch is removed from the raw jobName
        if(gitBranch != null && !gitBranch.isEmpty()) {
            // First, we try to remove the non-encoded git branch.
            jobNameNoBranch = rawJobName.replace("/" + gitBranch, "");

            try {
                // If the job name contains the git branch, that can have encoded characters.
                // e.g. jobname: pipeline/feature%2Fone --> it corresponds with the real git branch feature/one
                if(jobNameNoBranch.equals(rawJobName)) {
                    jobNameNoBranch = rawJobName.replace("/" + URLEncoder.encode(gitBranch, "UTF-8"), "");
                }
            } catch (UnsupportedEncodingException e){
                jobNameNoBranch = rawJobName;
            }
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
