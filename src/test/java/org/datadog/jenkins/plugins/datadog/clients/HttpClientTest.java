package org.datadog.jenkins.plugins.datadog.clients;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpClientTest {

    private HttpServer server;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void sendsRequestHeadersAndBody() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedHeader = new AtomicReference<>();
        server.createContext("/post", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedHeader.set(exchange.getRequestHeaders().getFirst("X-Test"));
            respond(exchange, 200, "accepted");
        });

        HttpClient client = new HttpClient(5000);
        String response = client.post(url("/post"), Collections.singletonMap("X-Test", "value"),
                "text/plain", "payload".getBytes(StandardCharsets.UTF_8), content -> content);

        assertEquals("accepted", response);
        assertEquals("payload", receivedBody.get());
        assertEquals("value", receivedHeader.get());
    }

    @Test
    public void streamsBinaryResponse() throws Exception {
        byte[] expected = {0, 1, -1};
        server.createContext("/binary", exchange -> {
            exchange.sendResponseHeaders(200, expected.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(expected);
            }
        });

        AtomicReference<byte[]> received = new AtomicReference<>();
        new HttpClient(5000).getBinary(url("/binary"), Collections.emptyMap(), stream -> {
            try {
                received.set(stream.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        assertArrayEquals(expected, received.get());
    }

    @Test
    public void retriesServerErrors() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/retry", exchange -> {
            int attempt = requests.incrementAndGet();
            respond(exchange, attempt == 1 ? 500 : 200, attempt == 1 ? "retry" : "ok");
        });

        assertEquals("ok", new HttpClient(5000).get(url("/retry"), Collections.emptyMap(), content -> content));
        assertEquals(2, requests.get());
    }

    @Test
    public void sendsAsynchronousRequest() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/async", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "ok");
            received.countDown();
        });

        new HttpClient(5000).postAsynchronously(url("/async"), Collections.emptyMap(),
                "text/plain", "payload".getBytes(StandardCharsets.UTF_8));

        assertTrue(received.await(10, TimeUnit.SECONDS));
        assertEquals("payload", body.get());
    }

    @Test
    public void retriesAsynchronousServerErrors() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch retried = new CountDownLatch(1);
        server.createContext("/async-retry", exchange -> {
            int attempt = requests.incrementAndGet();
            respond(exchange, attempt == 1 ? 500 : 200, attempt == 1 ? "retry" : "ok");
            if (attempt == 2) {
                retried.countDown();
            }
        });

        new HttpClient(5000).postAsynchronously(url("/async-retry"), Collections.emptyMap(),
                "text/plain", "payload".getBytes(StandardCharsets.UTF_8));

        assertTrue(retried.await(10, TimeUnit.SECONDS));
        assertEquals(2, requests.get());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
