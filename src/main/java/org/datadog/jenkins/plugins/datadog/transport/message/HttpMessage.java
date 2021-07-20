package org.datadog.jenkins.plugins.datadog.transport.message;

import java.net.URL;

public class HttpMessage {

    private final URL url;
    private final String method;
    private final String contentType;
    private final byte[] payload;

    public HttpMessage(URL url, String method, String contentType, byte[] payload) {
        this.url = url;
        this.method = method;
        this.contentType = contentType;
        this.payload = payload;
    }

    public URL getURL() {
        return this.url;
    }

    public String getMethod() {
        return this.method;
    }

    public String getContentType() {
        return this.contentType;
    }

    public byte[] getPayload() {
        return this.payload;
    }
}
