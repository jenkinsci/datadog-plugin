package org.datadog.jenkins.plugins.datadog.clients;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.transport.HttpMessage;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class HttpClient {

    private static final org.eclipse.jetty.client.HttpClient CLIENT = buildHttpClient();

    private static final String MAX_THREADS_ENV_VAR = "DD_JENKINS_HTTP_CLIENT_MAX_THREADS";
    private static final String MIN_THREADS_ENV_VAR = "DD_JENKINS_HTTP_CLIENT_MIN_THREADS";
    private static final String IDLE_THREAD_TIMEOUT_MILLIS_ENV_VAR = "DD_JENKINS_HTTP_CLIENT_IDLE_THREAD_TIMEOUT";
    private static final String RESERVED_THREADS_ENV_VAR = "DD_JENKINS_HTTP_CLIENT_RESERVED_THREADS";
    private static final String MAX_REQUEST_RETRIES_ENV_VAR = "DD_JENKINS_HTTP_CLIENT_REQUEST_RETRIES";
    private static final String INITIAL_RETRY_DELAY_MILLIS_ENV_VAR = "DD_JENKINS_HTTP_CLIENT_INITIAL_RETRY_DELAY";
    private static final String RETRY_DELAY_FACTOR_ENV_VAR = "DD_JENKINS_HTTP_CLIENT_RETRY_DELAY_FACTOR";
    private static final String MAX_RESPONSE_LENGTH_BYTES_ENV_VAR = "DD_JENKINS_HTTP_CLIENT_MAX_RESPONSE_LENGTH";
    private static final int MAX_THREADS_DEFAULT = 64;
    private static final int MIN_THREADS_DEFAULT = 1;
    private static final int IDLE_THREAD_TIMEOUT_MILLIS = 60_000;
    private static final int RESERVED_THREADS_DEFAULT = -1;
    private static final int MAX_REQUEST_RETRIES_DEFAULT = 5;
    private static final int INITIAL_RETRY_DELAY_MILLIS_DEFAULT = 100;
    private static final double RETRY_DELAY_FACTOR_DEFAULT = 2.0;
    private static final int MAX_RESPONSE_LENGTH_BYTES_DEFAULT = 64 * 1024 * 1024; // 64 MB
    private static volatile hudson.ProxyConfiguration EFFECTIVE_PROXY_CONFIGURATION;

    private static final Logger logger = Logger.getLogger(HttpClient.class.getName());

    private static org.eclipse.jetty.client.HttpClient buildHttpClient() {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1024);
        ThreadFactory threadFactory = new ThreadFactory() {
            final ThreadFactory delegate = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(final Runnable r) {
                final Thread result = delegate.newThread(r);
                result.setName("dd-http-client-" + result.getName());
                result.setDaemon(true);
                return result;
            }
        };

        QueuedThreadPool threadPool = new QueuedThreadPool(
                getEnv(MAX_THREADS_ENV_VAR, MAX_THREADS_DEFAULT),
                getEnv(MIN_THREADS_ENV_VAR, MIN_THREADS_DEFAULT),
                getEnv(IDLE_THREAD_TIMEOUT_MILLIS_ENV_VAR, IDLE_THREAD_TIMEOUT_MILLIS),
                getEnv(RESERVED_THREADS_ENV_VAR, RESERVED_THREADS_DEFAULT),
                queue,
                null,
                threadFactory
        );
        threadPool.setName("dd-http-client-thread-pool");

        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        org.eclipse.jetty.client.HttpClient httpClient = new org.eclipse.jetty.client.HttpClient(sslContextFactory);
        httpClient.setExecutor(threadPool);
        try {
            httpClient.start();
        } catch (Exception e) {
            throw new RuntimeException("Could not start HTTP client", e);
        }
        return httpClient;
    }

    private static void ensureProxyConfiguration() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return;
        }
        hudson.ProxyConfiguration jenkinsProxyConfiguration = jenkins.getProxy();
        if (jenkinsProxyConfiguration == null) {
            return;
        }

        if (EFFECTIVE_PROXY_CONFIGURATION != jenkinsProxyConfiguration) {
            synchronized (CLIENT) {
                if (EFFECTIVE_PROXY_CONFIGURATION != jenkinsProxyConfiguration) {
                    org.eclipse.jetty.client.ProxyConfiguration proxyConfig = CLIENT.getProxyConfiguration();
                    List<ProxyConfiguration.Proxy> proxies = proxyConfig.getProxies();
                    proxies.clear();

                    String proxyHost = jenkinsProxyConfiguration.getName();
                    int proxyPort = jenkinsProxyConfiguration.getPort();
                    List<Pattern> noProxyHostPatterns = jenkinsProxyConfiguration.getNoProxyHostPatterns();
                    proxies.add(new HttpProxy(proxyHost, proxyPort) {
                        @Override
                        public boolean matches(Origin origin) {
                            Origin.Address address = origin.getAddress();
                            String host = address.getHost();
                            for (Pattern noProxyHostPattern : noProxyHostPatterns) {
                                if (noProxyHostPattern.matcher(host).matches()) {
                                    return false;
                                }
                            }
                            return true;
                        }
                    });

                    EFFECTIVE_PROXY_CONFIGURATION = jenkinsProxyConfiguration;
                }
            }
        }
    }

    private final long timeoutMillis;
    private final HttpRetryPolicy.Factory retryPolicyFactory;

    public HttpClient(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        this.retryPolicyFactory = new HttpRetryPolicy.Factory(
                getEnv(MAX_REQUEST_RETRIES_ENV_VAR, MAX_REQUEST_RETRIES_DEFAULT),
                getEnv(INITIAL_RETRY_DELAY_MILLIS_ENV_VAR, INITIAL_RETRY_DELAY_MILLIS_DEFAULT),
                getEnv(RETRY_DELAY_FACTOR_ENV_VAR, RETRY_DELAY_FACTOR_DEFAULT));
    }

    public <T> T get(String url, Map<String, String> headers, Function<String, T> responseParser) throws ExecutionException, InterruptedException, TimeoutException {
        ensureProxyConfiguration();

        return executeSynchronously(
                requestSupplier(url, HttpMethod.GET, headers, null, null),
                retryPolicyFactory.create(),
                responseParser);
    }

    public <T> T post(String url, Map<String, String> headers, String contentType, byte[] body, Function<String, T> responseParser) throws ExecutionException, InterruptedException, TimeoutException {
        ensureProxyConfiguration();

        return executeSynchronously(
                requestSupplier(url, HttpMethod.POST, headers, contentType, body),
                retryPolicyFactory.create(),
                responseParser);
    }

    public void postAsynchronously(String url, Map<String, String> headers, String contentType, byte[] body) {
        ensureProxyConfiguration();

        executeAsynchronously(
                requestSupplier(
                        url,
                        HttpMethod.POST,
                        headers,
                        contentType,
                        body),
                retryPolicyFactory.create()
        );
    }

    public void sendAsynchronously(HttpMessage message) {
        ensureProxyConfiguration();

        String url = message.getURL().toString();
        HttpMethod httpMethod = HttpMethod.fromString(message.getMethod().name());
        String contentType = message.getContentType();
        byte[] body = message.getPayload();
        executeAsynchronously(
                requestSupplier(
                        url,
                        httpMethod,
                        Collections.emptyMap(),
                        contentType,
                        body),
                retryPolicyFactory.create()
        );
    }

    private Supplier<Request> requestSupplier(String url, HttpMethod method, Map<String, String> headers, String contentType, byte[] body) {
        return () -> {
            Request request = CLIENT
                    .newRequest(url)
                    .method(method)
                    .timeout(timeoutMillis, TimeUnit.MILLISECONDS);
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.header(e.getKey(), e.getValue());
            }
            if (contentType != null) {
                request.header(HttpHeader.CONTENT_TYPE, contentType);
            }
            if (body != null) {
                request.content(new BytesContentProvider(contentType, body));
            }
            return request;
        };
    }

    private static <T> T executeSynchronously(Supplier<Request> requestSupplier, HttpRetryPolicy retryPolicy, Function<String, T> responseParser) throws InterruptedException, TimeoutException, ExecutionException {
        while (true) {
            ContentResponse response;
            try {
                Request request = requestSupplier.get();
                response = request.send();
            } catch (TimeoutException | ExecutionException e) {
                if (retryPolicy.shouldRetry(null)) {
                    Thread.sleep(retryPolicy.backoff());
                    continue;
                } else {
                    throw e;
                }
            }

            int status = response.getStatus();
            if (status >= 200 && status < 300) {
                if (responseParser == null) {
                    return null;
                }

                String content = response.getContentAsString();
                return responseParser.apply(content);

            } else {
                if (retryPolicy.shouldRetry(response)) {
                    Thread.sleep(retryPolicy.backoff());
                    continue;
                }

                String additionalHint;
                switch (status) {
                    case HttpStatus.FORBIDDEN_403:
                        additionalHint = "API key might be invalid, please check your config";
                        break;
                    case HttpStatus.NOT_FOUND_404:
                    case HttpStatus.BAD_REQUEST_400:
                        additionalHint = "Request URL might be invalid, please check your config";
                        break;
                    default:
                        additionalHint = "";
                        break;
                }

                throw new ResponseProcessingException("Received erroneous response " + response + ". "  + additionalHint);
            }
        }
    }

    private static void executeAsynchronously(Supplier<Request> requestSupplier, HttpRetryPolicy retryPolicy) {
        Request request = requestSupplier.get();
        request.send(new ResponseListener(getEnv(MAX_RESPONSE_LENGTH_BYTES_ENV_VAR, MAX_RESPONSE_LENGTH_BYTES_DEFAULT), requestSupplier, retryPolicy));
    }

    private static final class ResponseListener extends BufferingResponseListener {
        private final Supplier<Request> requestSupplier;
        private final HttpRetryPolicy retryPolicy;

        public ResponseListener(int maxLength, Supplier<Request> requestSupplier, HttpRetryPolicy retryPolicy) {
            super(maxLength);
            this.requestSupplier = requestSupplier;
            this.retryPolicy = retryPolicy;
        }

        @Override
        public void onComplete(Result result) {
            try {
                Throwable failure = result.getFailure();
                if (failure != null) {
                    if (retryPolicy.shouldRetry(null)) {
                        Thread.sleep(retryPolicy.backoff());
                        requestSupplier.get().send(this);
                    } else {
                        DatadogUtilities.severe(logger, failure, "HTTP request failed: " + result.getRequest());
                    }
                }

                Response response = result.getResponse();
                int responseCode = response != null ? response.getStatus() : -1;
                if (responseCode > 0 && responseCode < 400) {
                    // successful response
                    return;
                }

                if (retryPolicy.shouldRetry(response)) {
                    Thread.sleep(retryPolicy.backoff());
                    requestSupplier.get().send(this);
                } else {
                    DatadogUtilities.severe(logger, null, "HTTP request failed: " + result.getRequest() + ", response: " + response);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class ResponseProcessingException extends ExecutionException {
        public ResponseProcessingException(String message) {
            super(message);
        }
    }

    private static int getEnv(String envVar, int defaultValue) {
        String value = System.getenv(envVar);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                DatadogUtilities.severe(logger, null, "Invalid value " + value + " provided for env var " + envVar + ": integer number expected");
            }
        }
        return defaultValue;
    }

    private static double getEnv(String envVar, double defaultValue) {
        String value = System.getenv(envVar);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (Exception e) {
                DatadogUtilities.severe(logger, null, "Invalid value " + value + " provided for env var " + envVar + ": double number expected");
            }
        }
        return defaultValue;
    }
}
