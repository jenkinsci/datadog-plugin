package org.datadog.jenkins.plugins.datadog.clients;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class JsonPayloadBatcher {

    private static final Logger logger = Logger.getLogger(JsonPayloadBatcher.class.getName());

    private static final byte[] BEGIN_JSON_ARRAY = "[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] END_JSON_ARRAY = "]".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMA = ",".getBytes(StandardCharsets.UTF_8);

    private final HttpClient httpClient;
    private final String url;
    private final Map<String, String> headers;
    private final boolean enableBatching; // TODO remove this flag in the next release

    public JsonPayloadBatcher(HttpClient httpClient, String url, Map<String, String> headers, boolean enableBatching) {
        this.httpClient = httpClient;
        this.url = url;
        this.headers = headers;
        this.enableBatching = enableBatching;
    }

    public <T> void postInCompressedBatches(Collection<T> payloads, Function<T, String> payloadToString, int batchLimitBytes) throws Exception {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(request);
        // the backend checks the size limit against the uncompressed body of the request
        int uncompressedRequestLength = 0;

        for (T payload : payloads) {
            byte[] body = payloadToString.apply(payload).getBytes(StandardCharsets.UTF_8);
            if (body.length + 2 > batchLimitBytes) { // + 2 is for array beginning and end: [<payload>]
                logger.severe("Dropping a payload because size (" + body.length + ") exceeds the allowed limit of " + batchLimitBytes);
                continue;
            }

            if (!enableBatching && uncompressedRequestLength > 0 ||
                    uncompressedRequestLength + body.length + 2 > batchLimitBytes) { // + 2 is for comma and array end: ,<payload>]
                gzip.write(END_JSON_ARRAY);
                gzip.close();
                httpClient.post(url, headers, "application/json", request.toByteArray(), Function.identity());
                request = new ByteArrayOutputStream();
                gzip = new GZIPOutputStream(request);
                uncompressedRequestLength = 0;
            }

            gzip.write(uncompressedRequestLength == 0 ? BEGIN_JSON_ARRAY : COMMA);
            gzip.write(body);
            uncompressedRequestLength += body.length + 1;
        }

        gzip.write(END_JSON_ARRAY);
        gzip.close();
        httpClient.post(url, headers, "application/json", request.toByteArray(), Function.identity());
    }
}
