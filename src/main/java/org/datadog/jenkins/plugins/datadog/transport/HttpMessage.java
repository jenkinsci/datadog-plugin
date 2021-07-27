package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;

import java.net.URL;

@SuppressFBWarnings
public class HttpMessage {

    private final URL url;
    private final HttpMethod method;
    private final String contentType;
    private final byte[] payload;

    public HttpMessage(URL url, HttpMethod method, String contentType, byte[] payload) {
        this.url = url;
        this.method = method;
        this.contentType = contentType;
        this.payload = payload;
    }

    public URL getURL() {
        return this.url;
    }

    public HttpMethod getMethod() {
        return this.method;
    }

    public String getContentType() {
        return this.contentType;
    }

    public byte[] getPayload() {
        return this.payload;
    }

    public enum HttpMethod {
        PUT
    }
}
