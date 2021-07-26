package org.datadog.jenkins.plugins.datadog.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeHttpSender extends HttpSender {
    private static final HttpErrorHandler NO_OP = new HttpErrorHandler() {
        @Override
        public void handle(Exception exception) {
            // N/A
        }
    };

    private static final Logger log = LoggerFactory.getLogger(FakeHttpSender.class);

    private final List<HttpMessage> httpMessages = new CopyOnWriteArrayList<>();
    private final List<CountDownLatch> latches = new ArrayList();
    private final AtomicInteger messageCount = new AtomicInteger();

    FakeHttpSender(BlockingQueue<HttpMessage> queue) {
        super(queue, NO_OP, 1000);
    }

    @Override
    protected void blockingSend(HttpMessage message) {
        this.messageCount.incrementAndGet();
        synchronized (this.latches) {
            httpMessages.add(message);
            for(final CountDownLatch latch : latches) {
                if(httpMessages.size() >= latch.getCount()) {
                    while (latch.getCount() > 0) {
                        latch.countDown();
                    }
                }
            }
        }
    }

    public boolean waitForMessagesMax(final int number, int seconds) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(number);
        synchronized (latches) {
            if (httpMessages.size() >= number) {
                return true;
            }
            latches.add(latch);
        }
        return latch.await(seconds, TimeUnit.SECONDS);
    }

    public void waitForMessages(final int number) throws InterruptedException, TimeoutException {
        if (!waitForMessagesMax(number, 20)) {
            String msg = "Timeout waiting for " + number + " message(s). messages.size() == " + httpMessages.size();
            log.warn(msg);
            throw new TimeoutException(msg);
        }
    }

    public List<HttpMessage> getHttpMessages() {
        return httpMessages;
    }
}
