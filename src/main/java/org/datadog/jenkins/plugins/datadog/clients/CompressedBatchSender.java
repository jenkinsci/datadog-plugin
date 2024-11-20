package org.datadog.jenkins.plugins.datadog.clients;

import net.sf.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class CompressedBatchSender<T> implements JsonPayloadSender<T> {

    private static final Logger logger = Logger.getLogger(CompressedBatchSender.class.getName());

    private static final byte[] BEGIN_JSON_ARRAY = "[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] END_JSON_ARRAY = "]".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMA = ",".getBytes(StandardCharsets.UTF_8);

    private final HttpClient httpClient;
    private final String url;
    private final Map<String, String> headers;
    private final int batchLimitBytes;
    private final Function<T, JSONObject> payloadToJson;

    public CompressedBatchSender(HttpClient httpClient,
                                 String url,
                                 Map<String, String> headers,
                                 int batchLimitBytes,
                                 Function<T, JSONObject> payloadToJson) {
        this.httpClient = httpClient;
        this.url = url;
        this.headers = headers;
        this.batchLimitBytes = batchLimitBytes;
        this.payloadToJson = payloadToJson;
    }

    @Override
    public void send(Collection<T> payloads) throws Exception {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(request);
        // the backend checks the size limit against the uncompressed body of the request
        int uncompressedRequestLength = 0;

        for (T payload : payloads) {
            JSONObject json = payloadToJson.apply(payload);
            byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
            if (body.length + 2 > batchLimitBytes) { // + 2 is for array beginning and end: [<payload>]
                logger.severe("Dropping a payload because size (" + body.length + ") exceeds the allowed limit of " + batchLimitBytes);
                continue;
            }

            if (uncompressedRequestLength + body.length + 2 > batchLimitBytes) { // + 2 is for comma and array end: ,<payload>]
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
