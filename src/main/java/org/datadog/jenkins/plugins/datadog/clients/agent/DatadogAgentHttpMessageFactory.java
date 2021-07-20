package org.datadog.jenkins.plugins.datadog.clients.agent;

import java.net.URL;

public class DatadogAgentHttpMessageFactory {

    private final URL agentURL;
    private final HttpMethod httpMethod;
    private final PayloadMapper payloadMapper;

    private DatadogAgentHttpMessageFactory(final Builder builder) {
        this.agentURL = builder.agentURL;
        this.httpMethod = builder.httpMethod;
        this.payloadMapper = builder.payloadMapper;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private URL agentURL;
        private HttpMethod httpMethod;
        private PayloadMapper payloadMapper;

        public Builder agentURL(URL agentURL) {
            this.agentURL = agentURL;
            return this;
        }

        public Builder httpMethod(final HttpMethod httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder payloadMapper(final PayloadMapper payloadMapper) {
            this.payloadMapper = payloadMapper;
            return this;
        }

        public DatadogAgentHttpMessageFactory build() {
            return new DatadogAgentHttpMessageFactory(this);
        }
    }

    public HttpMessage create(Object obj) {
        return new HttpMessage(this.agentURL, this.httpMethod.name(), this.payloadMapper.contentType(), this.payloadMapper.map(obj));
    }
}
