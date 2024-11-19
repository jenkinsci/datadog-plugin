package org.datadog.jenkins.plugins.datadog.clients;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import net.sf.json.JSONObject;

public class SimpleSender<T> implements JsonPayloadSender<T> {

    private final HttpClient httpClient;
    private final String url;
    private final Map<String, String> headers;
    private final Function<T, JSONObject> payloadToJson;

    public SimpleSender(HttpClient httpClient,
                        String url,
                        Map<String, String> headers,
                        Function<T, JSONObject> payloadToJson) {
        this.httpClient = httpClient;
        this.url = url;
        this.headers = headers;
        this.payloadToJson = payloadToJson;
    }

    @Override
    public void send(Collection<T> payloads) throws Exception {
        for (T payload : payloads) {
            JSONObject json = payloadToJson.apply(payload);
            byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
            httpClient.postAsynchronously(url, headers, "application/json", body);
        }
    }
}
