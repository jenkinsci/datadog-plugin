package org.datadog.jenkins.plugins.datadog.clients;

import net.sf.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class BatchSender<T> implements JsonPayloadSender<T> {

    private static final Logger logger = Logger.getLogger(BatchSender.class.getName());

    private static final byte[] BEGIN_JSON_ARRAY = "[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] END_JSON_ARRAY = "]".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMA = ",".getBytes(StandardCharsets.UTF_8);

    private final HttpClient httpClient;
    private final String url;
    private final Map<String, String> headers;
    private final int batchLimitBytes;
    private final Function<T, JSONObject> payloadToJson;
    private final boolean compress;

    public BatchSender(HttpClient httpClient,
                       String url,
                       Map<String, String> headers,
                       int batchLimitBytes,
                       Function<T, JSONObject> payloadToJson,
                       boolean compress) {
        this.httpClient = httpClient;
        this.url = url;
        this.headers = new HashMap<>(headers);
        this.batchLimitBytes = batchLimitBytes;
        this.payloadToJson = payloadToJson;
        this.compress = compress;

        if (compress) {
            this.headers.put("Content-Encoding", "gzip");
        }
    }

    @Override
    public void send(Collection<T> payloads) throws Exception {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        OutputStream output = compress ? new GZIPOutputStream(request) : request;

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
                output.write(END_JSON_ARRAY);
                output.close();
                httpClient.post(url, headers, "application/json", request.toByteArray(), Function.identity());
                request = new ByteArrayOutputStream();
                output = compress ? new GZIPOutputStream(request) : request;
                uncompressedRequestLength = 0;
            }

            output.write(uncompressedRequestLength == 0 ? BEGIN_JSON_ARRAY : COMMA);
            output.write(body);
            uncompressedRequestLength += body.length + 1;
        }

        output.write(END_JSON_ARRAY);
        output.close();
        httpClient.post(url, headers, "application/json", request.toByteArray(), Function.identity());
    }
}
