package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.FilePath;
import hudson.model.Node;
import hudson.util.Secret;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;

final class JavaConfigurator implements TracerConfigurator {

    private static final String TRACER_DISTRIBUTION_URL = "https://dtdg.co/latest-java-tracer";
    private static final String TRACER_FILE_NAME = "dd-java-agent.jar";
    private static final String TRACER_IGNORE_JENKINS_PROXY_ENV_VAR = "DATADOG_JENKINS_PLUGIN_TRACER_IGNORE_JENKINS_PROXY";
    private static final String TRACER_JAR_CACHE_TTL_ENV_VAR = "DATADOG_JENKINS_PLUGIN_TRACER_JAR_CACHE_TTL_MINUTES";
    private static final int DEFAULT_TRACER_JAR_CACHE_TTL_MINUTES = 60 * 12;

    private final HttpClient httpClient = new HttpClient(60_000);

    @Override
    public Map<String, String> configure(DatadogTracerJobProperty<?> tracerConfig, Node node, FilePath workspacePath, Map<String, String> envs) throws Exception {
        FilePath tracerFile = downloadTracer(tracerConfig, workspacePath);
        return getEnvVariables(tracerConfig, node, tracerFile, envs);
    }

    private FilePath downloadTracer(DatadogTracerJobProperty<?> tracerConfig, FilePath workspacePath) throws Exception {
        FilePath datadogFolder = workspacePath.child(".datadog");
        datadogFolder.mkdirs();

        FilePath datadogTracerFile = datadogFolder.child(TRACER_FILE_NAME);
        long minutesSinceModification = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - datadogTracerFile.lastModified());
        if (minutesSinceModification < getTracerJarCacheTtlMinutes(tracerConfig)) {
            // downloaded tracer is fresh enough
            return datadogTracerFile.absolutize();
        }

        httpClient.getBinary(TRACER_DISTRIBUTION_URL, Collections.emptyMap(), is -> {
            try {
                datadogTracerFile.copyFrom(is);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return datadogTracerFile.absolutize();
    }

    private int getTracerJarCacheTtlMinutes(DatadogTracerJobProperty<?> tracerConfig) {
        Map<String, String> additionalVariables = tracerConfig.getAdditionalVariables();
        if (additionalVariables != null) {
            String envVariable = additionalVariables.get(TRACER_JAR_CACHE_TTL_ENV_VAR);
            if (envVariable != null) {
                try {
                    return Integer.parseInt(envVariable);
                } catch (Exception e) {
                    // ignored
                }
            }
        }

        String envVariable = System.getenv(TRACER_JAR_CACHE_TTL_ENV_VAR);
        if (envVariable != null) {
            try {
                return Integer.parseInt(envVariable);
            } catch (Exception e) {
                // ignored
            }
        }

        return DEFAULT_TRACER_JAR_CACHE_TTL_MINUTES;
    }

    private static Map<String, String> getEnvVariables(DatadogTracerJobProperty<?> tracerConfig,
                                                       Node node,
                                                       FilePath tracerFile,
                                                       Map<String, String> envs) {
        Map<String, String> variables = new HashMap<>();

        String tracerAgent = "-javaagent:" + tracerFile.getRemote();
        variables.put("MAVEN_OPTS", PropertyUtils.prepend(envs, "MAVEN_OPTS", tracerAgent));
        variables.put("GRADLE_OPTS", PropertyUtils.prepend(envs, "GRADLE_OPTS", "-Dorg.gradle.jvmargs=" + tracerAgent));

        String proxyConfiguration = getProxyConfiguration(tracerConfig, node);
        if (proxyConfiguration != null) {
            variables.put("JAVA_TOOL_OPTIONS", PropertyUtils.prepend(envs, "JAVA_TOOL_OPTIONS", proxyConfiguration));
        }

        Map<String, String> additionalVariables = tracerConfig.getAdditionalVariables();
        if (additionalVariables != null) {
            variables.putAll(additionalVariables);
        }

        return variables;
    }

    private static String getProxyConfiguration(DatadogTracerJobProperty<?> tracerConfig, Node node) {
        if (!(node instanceof Jenkins)) {
            // only apply Jenkins proxy settings if tracer will be run on master node
            return null;
        }

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }
        hudson.ProxyConfiguration jenkinsProxyConfiguration = jenkins.getProxy();
        if (jenkinsProxyConfiguration == null) {
            return null;
        }

        Map<String, String> additionalVariables = tracerConfig.getAdditionalVariables();
        if (Boolean.parseBoolean(additionalVariables.get(TRACER_IGNORE_JENKINS_PROXY_ENV_VAR))) {
            return null;
        }

        String proxyHost = jenkinsProxyConfiguration.getName();
        int proxyPort = jenkinsProxyConfiguration.getPort();
        String noProxyHost = jenkinsProxyConfiguration.getNoProxyHost();
        String userName = jenkinsProxyConfiguration.getUserName();
        Secret password = jenkinsProxyConfiguration.getSecretPassword();

        StringBuilder proxyOptions = new StringBuilder();
        if (proxyHost != null) {
            proxyOptions.append("-Dhttp.proxyHost=").append(proxyHost);
        }
        if (proxyPort > 0) {
            proxyOptions.append("-Dhttp.proxyPort=").append(proxyPort);
        }
        if (noProxyHost != null) {
            proxyOptions.append("-Dhttp.nonProxyHosts=").append(noProxyHost);
        }
        if (userName != null) {
            proxyOptions.append("-Dhttp.proxyUser=").append(userName);
        }
        if (password != null) {
            proxyOptions.append("-Dhttp.proxyPassword=").append(Secret.toString(password));
        }
        return proxyOptions.length() > 0 ? proxyOptions.toString() : null;
    }
}
