package org.datadog.jenkins.plugins.datadog.clients;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class CompressedBatchSenderTest {

    private static final String URL = "dummyUrl";

    public static final Map<String, String> HEADERS;
    static {
        HEADERS = new HashMap<>();
        HEADERS.put("header1", "value1");
        HEADERS.put("header2", "value2");
    }

    private static final int BATCH_SIZE = 30;

    private final HttpClient httpClient = mock(HttpClient.class);

    private final CompressedBatchSender<Map<String, String>> sender = new CompressedBatchSender<>(httpClient, URL, HEADERS, BATCH_SIZE, JSONObject::fromObject);

    @Test
    public void testOneElementBatch() throws Exception{
        List<Collection<Map<String, String>>> batches = whenSending(map("a", "b"));

        assertEquals(1, batches.size());
        assertEquals(Arrays.asList(map("a", "b")), batches.get(0));
    }

    @Test
    public void testTwoElementBatch() throws Exception{
        List<Collection<Map<String, String>>> batches = whenSending(map("a", "b"), map("c", "d"));

        assertEquals(1, batches.size());
        assertEquals(Arrays.asList(map("a", "b"), map("c", "d")), batches.get(0));
    }

    @Test
    public void testTwoBatches() throws Exception{
        List<Collection<Map<String, String>>> batches = whenSending(map("a", "b"), map("c", "d"), map("e", "f"), map("g", "h"));

        assertEquals(2, batches.size());
        assertEquals(Arrays.asList(map("a", "b"), map("c", "d")), batches.get(0));
        assertEquals(Arrays.asList(map("e", "f"), map("g", "h")), batches.get(1));
    }

    @Test
    public void testBigElement() throws Exception{
        List<Collection<Map<String, String>>> batches = whenSending(map("a", "b"), map("abcdefghijk", "1234567890"), map("e", "f"));

        assertEquals(3, batches.size());
        assertEquals(Arrays.asList(map("a", "b")), batches.get(0));
        assertEquals(Arrays.asList(map("abcdefghijk", "1234567890")), batches.get(1));
        assertEquals(Arrays.asList(map("e", "f")), batches.get(2));
    }

    @Test
    public void testHugeElementsAreDropped() throws Exception{
        List<Collection<Map<String, String>>> batches = whenSending(map("a", "b"), map("abcdefghijk", "12345678901234567890123456789012345678901234567890"), map("e", "f"));

        assertEquals(1, batches.size());
        assertEquals(Arrays.asList(map("a", "b"), map("e", "f")), batches.get(0));
    }

    @Test
    public void testHugeElementsAreDroppedEdgeCase() throws Exception {
        // second element size is 31, which is 1 byte more than the limit
        List<Collection<Map<String, String>>> batches = whenSending(map("a", "b"), map("abcdefghijk", "12345678901"), map("e", "f"));

        assertEquals(1, batches.size());
        assertEquals(Arrays.asList(map("a", "b"), map("e", "f")), batches.get(0));
    }

    @Test
    public void testTwoBatchesEdgeCase() throws Exception {
        // sum of sizes of two elements is 31, which is 1 byte more than the limit
        List<Collection<Map<String, String>>> batches = whenSending(map("a", "b"), map("cd", "1234567890"));

        assertEquals(2, batches.size());
        assertEquals(Arrays.asList(map("a", "b")), batches.get(0));
        assertEquals(Arrays.asList(map("cd", "1234567890")), batches.get(1));
    }

    private List<Collection<Map<String, String>>> whenSending(Map<String, String>... payloads) throws Exception {
        sender.send(Arrays.asList(payloads));

        List<Collection<Map<String, String>>> batches = new ArrayList<>();
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(httpClient, atLeast(1)).post(eq(URL), eq(HEADERS), eq("application/json"), captor.capture(), any());
        List<byte[]> requestsBytes = captor.getAllValues();
        for (byte[] requestBytes : requestsBytes) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(requestBytes))) {
                byte[] uncompressedRequestBytes = IOUtils.toByteArray(gzip);
                String uncompressedRequest = new String(uncompressedRequestBytes);
                JSONArray requestJson = JSONArray.fromObject(uncompressedRequest);

                Collection<Map<String, String>> batch = new ArrayList<>();
                for(int i = 0; i < requestJson.size(); i++) {
                    batch.add((Map<String, String>) requestJson.getJSONObject(i).toBean(Map.class));
                }
                batches.add(batch);
            }
        }
        return batches;
    }

  private static @NotNull Map<String, String> map(String key, String value) {
    return Collections.singletonMap(key, value);
  }
}
