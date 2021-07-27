package org.datadog.jenkins.plugins.datadog.transport;

import static org.junit.Assert.*;

import org.junit.Test;

import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

public class HttpSenderTest {

    private static final HttpMessage SAMPLE_MESSAGE = new HttpMessage(buildURL("http://localhost"), null, null, null);

    @Test
    public void testHttpSenderConsumer() throws TimeoutException, InterruptedException {
        //Given
        final BlockingQueue<HttpMessage> queue = new LinkedBlockingQueue<>(10);
        final FakeHttpSender sender = new FakeHttpSender(queue);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(sender);

        //When
        sender.send(SAMPLE_MESSAGE);
        sender.send(SAMPLE_MESSAGE);
        sender.send(SAMPLE_MESSAGE);

        //Then
        sender.waitForMessages(3);
        assertEquals(3, sender.getHttpMessages().size());
    }


    private static URL buildURL(final String urlStr) {
        try {
            return new URL(urlStr);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}