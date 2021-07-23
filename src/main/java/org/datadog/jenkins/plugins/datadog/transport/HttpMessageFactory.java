package org.datadog.jenkins.plugins.datadog.transport;

import java.net.URL;
import java.util.List;

public class HttpMessageFactory {

    private final URL url;
    private final HttpMessage.HttpMethod httpMethod;
    private final PayloadMapper<List<PayloadMessage>> payloadMapper;

    private HttpMessageFactory(final Builder builder) {
        this.url = builder.agentURL;
        this.httpMethod = builder.httpMethod;
        this.payloadMapper = builder.payloadMapper;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private URL agentURL;
        private HttpMessage.HttpMethod httpMethod;
        private PayloadMapper<List<PayloadMessage>> payloadMapper;

        public Builder agentURL(URL agentURL) {
            this.agentURL = agentURL;
            return this;
        }

        public Builder httpMethod(final HttpMessage.HttpMethod httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder payloadMapper(final PayloadMapper payloadMapper) {
            this.payloadMapper = payloadMapper;
            return this;
        }

        public HttpMessageFactory build() {
            return new HttpMessageFactory(this);
        }
    }

    public URL getURL() {
        return this.url;
    }

    public HttpMessage create(List<PayloadMessage> messages) {
        return new HttpMessage(this.url, this.httpMethod, this.payloadMapper.contentType(), this.payloadMapper.map(messages));
    }
}
