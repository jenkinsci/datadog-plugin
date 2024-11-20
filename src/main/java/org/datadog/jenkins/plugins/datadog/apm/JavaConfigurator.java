package org.datadog.jenkins.plugins.datadog.apm;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.apm.signature.SignatureVerifier;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;
import org.datadog.jenkins.plugins.datadog.steps.TestVisibility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

final class JavaConfigurator implements TracerConfigurator {

    private static final Logger LOGGER = Logger.getLogger(JavaConfigurator.class.getName());

    private static final String TRACER_DISTRIBUTION_URL_ENV_VAR = "DATADOG_JENKINS_PLUGIN_TRACER_DISTRIBUTION_URL";
    private static final String DATADOG_PUBLIC_KEY_ENV_VAR = "DATADOG_JENKINS_PLUGIN_DATADOG_PUBLIC_KEY";
    private static final String DEFAULT_TRACER_DISTRIBUTION_URL = "https://dtdg.co/latest-java-tracer";
    private static final String TRACER_FILE_NAME = "dd-java-agent.jar";
    private static final String TRACER_IGNORE_JENKINS_PROXY_ENV_VAR = "DATADOG_JENKINS_PLUGIN_TRACER_IGNORE_JENKINS_PROXY";
    private static final String TRACER_JAR_CACHE_TTL_ENV_VAR = "DATADOG_JENKINS_PLUGIN_TRACER_JAR_CACHE_TTL_MINUTES";
    private static final int DEFAULT_TRACER_JAR_CACHE_TTL_MINUTES = 60 * 12;
    private static final int TRACER_DOWNLOAD_TIMEOUT_MILLIS = 60_000;

    private final HttpClient httpClient = new HttpClient(TRACER_DOWNLOAD_TIMEOUT_MILLIS);

    @Override
    public Map<String, String> configure(TestVisibility testVisibility, Node node, FilePath workspacePath, Map<String, String> envs, TaskListener listener) throws Exception {
        FilePath tracerFile = downloadTracer(testVisibility, workspacePath,node, listener);
        return getEnvVariables(testVisibility, node, tracerFile, envs);
    }

    private FilePath downloadTracer(TestVisibility testVisibility, FilePath workspacePath, Node node, TaskListener listener) throws Exception {
        FilePath datadogTracerFile = getDatadogTracerFile(workspacePath);
        long minutesSinceModification = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - datadogTracerFile.lastModified());
        if (minutesSinceModification < getTracerJarCacheTtlMinutes(testVisibility)) {
            listener.getLogger().println("[datadog] Configuring DD Java tracer: using existing tracer available at " + datadogTracerFile);
            // downloaded tracer is fresh enough
            return datadogTracerFile.absolutize();
        }

        String tracerDistributionUrl = getTracerDistributionUrl(testVisibility);
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
            byte[] signaturePublicKey = getTracerSignaturePublicKey(testVisibility);

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

    private static FilePath getDatadogTracerFile(FilePath workspacePath) throws IOException, InterruptedException {
        FilePath datadogFolder = workspacePath.child(".datadog");
        datadogFolder.mkdirs();
        return datadogFolder.child(TRACER_FILE_NAME);
    }

    private int getTracerJarCacheTtlMinutes(TestVisibility testVisibility) {
        return getSetting(testVisibility, TRACER_JAR_CACHE_TTL_ENV_VAR, DEFAULT_TRACER_JAR_CACHE_TTL_MINUTES, Integer::parseInt);
    }

    private String getTracerDistributionUrl(TestVisibility testVisibility) {
        return getSetting(testVisibility, TRACER_DISTRIBUTION_URL_ENV_VAR, DEFAULT_TRACER_DISTRIBUTION_URL, this::validateUserSuppliedTracerUrl);
    }

    private byte[] getTracerSignaturePublicKey(TestVisibility testVisibility) {
        return getSetting(testVisibility, DATADOG_PUBLIC_KEY_ENV_VAR, SignatureVerifier.DATADOG_PUBLIC_KEY.getBytes(StandardCharsets.UTF_8), String::getBytes);
    }

    private <T> T getSetting(TestVisibility testVisibility, String envVariableName, T defaultValue, Function<String, T> parser) {
        String envVariable = getEnvVariable(testVisibility, envVariableName);
        if (envVariable != null) {
            try {
                return parser.apply(envVariable);
            } catch (Exception e) {
                LOGGER.fine("Variable " + envVariableName + " has value which could not be parsed. " +
                        "Will use default value " + defaultValue);
            }
        }
        return defaultValue;
    }

    private String getEnvVariable(TestVisibility testVisibility, String name) {
        Map<String, String> additionalVariables = testVisibility.getAdditionalVariables();
        if (additionalVariables != null) {
            String envVariable = additionalVariables.get(name);
            if (envVariable != null) {
                return envVariable;
            }
        }
        return System.getenv(name);
    }

    private String validateUserSuppliedTracerUrl(String distributionUrl) {
        try {
            new URL(distributionUrl);
            return distributionUrl;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Error while parsing tracer distribution URL: " + distributionUrl, e);
        }
    }

    private static Map<String, String> getEnvVariables(TestVisibility testVisibility,
                                                       Node node,
                                                       FilePath tracerFile,
                                                       Map<String, String> envs) {
        Map<String, String> variables = new HashMap<>();

        String tracerAgent = "-javaagent:" + tracerFile.getRemote();
        variables.put("DD_TRACE_OPTS", PropertyUtils.prepend(envs, "DD_TRACE_OPTS", tracerAgent)); // this var can be used for "semi-automatic" tracer injection (e.g. `JAVA_TOOL_OPTIONS=$DD_TRACE_OPTS`)
        variables.put("MAVEN_OPTS", PropertyUtils.prepend(envs, "MAVEN_OPTS", tracerAgent));
        variables.put("SBT_OPTS", PropertyUtils.prepend(envs, "SBT_OPTS", tracerAgent));
        variables.put("ANT_OPTS", PropertyUtils.prepend(envs, "ANT_OPTS", tracerAgent));
        variables.put("GRADLE_OPTS", PropertyUtils.prepend(envs, "GRADLE_OPTS", "-Dorg.gradle.jvmargs=" + tracerAgent));

        String proxyConfiguration = getProxyConfiguration(testVisibility, node);
        if (proxyConfiguration != null) {
            variables.put("JAVA_TOOL_OPTIONS", PropertyUtils.prepend(variables, "JAVA_TOOL_OPTIONS", proxyConfiguration));
        }

        Map<String, String> additionalVariables = testVisibility.getAdditionalVariables();
        if (additionalVariables != null) {
            variables.putAll(additionalVariables);
        }

        return variables;
    }

    private static String getProxyConfiguration(TestVisibility testVisibility, Node node) {
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

        Map<String, String> additionalVariables = testVisibility.getAdditionalVariables();
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
            proxyOptions.append("-Dhttp.proxyHost=").append(proxyHost).append(" ");
        }
        if (proxyPort > 0) {
            proxyOptions.append("-Dhttp.proxyPort=").append(proxyPort).append(" ");
        }
        if (noProxyHost != null) {
            proxyOptions.append("-Dhttp.nonProxyHosts=").append(noProxyHost).append(" ");
        }
        if (userName != null) {
            proxyOptions.append("-Dhttp.proxyUser=").append(userName).append(" ");
        }
        if (password != null) {
            proxyOptions.append("-Dhttp.proxyPassword=").append(Secret.toString(password)).append(" ");
        }
        return proxyOptions.length() > 0 ? proxyOptions.toString() : null;
    }

    @Override
    public boolean isConfigurationValid(Node node, FilePath workspacePath) {
        try {
            FilePath datadogTracerFile = getDatadogTracerFile(workspacePath);
            return datadogTracerFile.exists();
        } catch (Exception e) {
            DatadogUtilities.logException(LOGGER, Level.FINE, "Could not verify Java tracer file existence", e);
            return false;
        }
    }
}
