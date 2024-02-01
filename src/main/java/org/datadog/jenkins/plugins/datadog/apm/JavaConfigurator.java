package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.Secret;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;
import org.datadog.jenkins.plugins.datadog.apm.signature.SignatureVerifier;

final class JavaConfigurator implements TracerConfigurator {

    private static final String TRACER_DISTRIBUTION_URL_ENV_VAR = "DATADOG_JENKINS_PLUGIN_TRACER_DISTRIBUTION_URL";
    private static final String DEFAULT_TRACER_DISTRIBUTION_URL = "https://dtdg.co/latest-java-tracer";
    private static final String TRACER_FILE_NAME = "dd-java-agent.jar";
    private static final String TRACER_IGNORE_JENKINS_PROXY_ENV_VAR = "DATADOG_JENKINS_PLUGIN_TRACER_IGNORE_JENKINS_PROXY";
    private static final String TRACER_JAR_CACHE_TTL_ENV_VAR = "DATADOG_JENKINS_PLUGIN_TRACER_JAR_CACHE_TTL_MINUTES";
    private static final int DEFAULT_TRACER_JAR_CACHE_TTL_MINUTES = 60 * 12;
    private static final int TRACER_DOWNLOAD_TIMEOUT_MILLIS = 60_000;
    private static final String DATADOG_DISTRIBUTION_HOST = "dtdg.co";
    private static final String MAVEN_CENTRAL_HOST = "repo1.maven.org";
    private static final String DATADOG_AGENT_MAVEN_DISTRIBUTION_PATH = "/maven2/com/datadoghq/dd-java-agent/";

    private final HttpClient httpClient = new HttpClient(TRACER_DOWNLOAD_TIMEOUT_MILLIS);

    @Override
    public Map<String, String> configure(DatadogTracerJobProperty<?> tracerConfig, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        FilePath tracerFile = downloadTracer(tracerConfig, workspacePath,node, listener);
        return getEnvVariables(tracerConfig, node, tracerFile, envs);
    }

    private FilePath downloadTracer(DatadogTracerJobProperty<?> tracerConfig, FilePath workspacePath, Node node, TaskListener listener) throws Exception {
        FilePath datadogFolder = workspacePath.child(".datadog");
        datadogFolder.mkdirs();

        FilePath datadogTracerFile = datadogFolder.child(TRACER_FILE_NAME);
        long minutesSinceModification = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - datadogTracerFile.lastModified());
        if (minutesSinceModification < getTracerJarCacheTtlMinutes(tracerConfig)) {
            listener.getLogger().println("[datadog] Configuring DD Java tracer: using existing tracer available at " + datadogTracerFile);
            // downloaded tracer is fresh enough
            return datadogTracerFile.absolutize();
        }

        String tracerDistributionUrl = getTracerDistributionUrl(tracerConfig);
        httpClient.getBinary(tracerDistributionUrl, Collections.emptyMap(), is -> {
            try {
                datadogTracerFile.copyFrom(is);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        listener.getLogger().println("[datadog] Configuring DD Java tracer: tracer installed in " + workspacePath + " on " + node);

        if (!DEFAULT_TRACER_DISTRIBUTION_URL.equals(tracerDistributionUrl)) {
            // verify signature if downloading from Maven Central
            String signatureFileUrl = tracerDistributionUrl + ".asc";
            byte[] signaturePublicKey = getTracerSignaturePublicKey(tracerConfig);

            httpClient.getBinary(signatureFileUrl, Collections.emptyMap(), signatureStream -> {
                try (InputStream tracerStream = datadogTracerFile.read();
                     InputStream publicKeyStream = new ByteArrayInputStream(signaturePublicKey)) {
                    boolean signatureValid = SignatureVerifier.verifySignature(tracerStream, signatureStream, publicKeyStream);
                    if (!signatureValid) {
                        throw new IllegalStateException("Tracer downloaded from " + tracerDistributionUrl + " is not signed with a valid signature");
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error while verifying tracer signature", e);
                }
            });
        }

        return datadogTracerFile.absolutize();
    }

    private int getTracerJarCacheTtlMinutes(DatadogTracerJobProperty<?> tracerConfig) {
        return getSetting(tracerConfig, TRACER_JAR_CACHE_TTL_ENV_VAR, DEFAULT_TRACER_JAR_CACHE_TTL_MINUTES, Integer::parseInt);
    }

    private String getTracerDistributionUrl(DatadogTracerJobProperty<?> tracerConfig) {
        return getSetting(tracerConfig, TRACER_DISTRIBUTION_URL_ENV_VAR, DEFAULT_TRACER_DISTRIBUTION_URL, this::validateUserSuppliedTracerUrl);
    }

    private byte[] getTracerSignaturePublicKey(DatadogTracerJobProperty<?> tracerConfig) {
        return getSetting(tracerConfig, TRACER_DISTRIBUTION_URL_ENV_VAR, SignatureVerifier.DATADOG_PUBLIC_KEY.getBytes(StandardCharsets.UTF_8), String::getBytes);
    }

    private <T> T getSetting(DatadogTracerJobProperty<?> tracerConfig, String envVariableName, T defaultValue, Function<String, T> parser) {
        String envVariable = getEnvVariable(tracerConfig, envVariableName);
        if (envVariable != null) {
            return parser.apply(envVariable);
        }
        return defaultValue;
    }

    private String getEnvVariable(DatadogTracerJobProperty<?> tracerConfig, String name) {
        Map<String, String> additionalVariables = tracerConfig.getAdditionalVariables();
        if (additionalVariables != null) {
            String envVariable = additionalVariables.get(name);
            if (envVariable != null) {
                return envVariable;
            }
        }
        return System.getenv(TRACER_JAR_CACHE_TTL_ENV_VAR);
    }

    private String validateUserSuppliedTracerUrl(String distributionUrl) {
        URL url;
        try {
            url = new URL(distributionUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Error while parsing tracer distribution URL: " + distributionUrl, e);
        }

        String host = url.getHost();
        if (DATADOG_DISTRIBUTION_HOST.equals(host)) {
            return distributionUrl;
        }

        if (!MAVEN_CENTRAL_HOST.equals(host)) {
            throw new IllegalArgumentException("Illegal tracer distribution host: " + host + " (" + distributionUrl + ")");
        }

        String path = url.getPath();
        if (!path.startsWith(DATADOG_AGENT_MAVEN_DISTRIBUTION_PATH)) {
            throw new IllegalArgumentException("Illegal tracer distribution path: " + path + " (" + distributionUrl + ")");
        }
        return distributionUrl;
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
