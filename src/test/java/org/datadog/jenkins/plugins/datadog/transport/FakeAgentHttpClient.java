package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeAgentHttpClient extends CopyOnWriteArrayList<TraceSpan> implements AgentHttpClient {
    private static final Logger log = LoggerFactory.getLogger(FakeAgentHttpClient.class);

    private final List<CountDownLatch> latches = new ArrayList();
    private final AtomicInteger traceCount = new AtomicInteger();

    @Override
    public void send(TraceSpan span) {
        System.out.println("--- send: " + span.getName());
        this.traceCount.incrementAndGet();
        synchronized (this.latches) {
            this.add(span);
            for(final CountDownLatch latch : latches) {
                if(size() >= latch.getCount()) {
                    while (latch.getCount() > 0) {
                        latch.countDown();
                    }
                }
            }
        }
    }

    public boolean waitForTracesMax(final int number, int seconds) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(number);
        synchronized (latches) {
            if (size() >= number) {
                return true;
            }
            latches.add(latch);
        }
        return latch.await(seconds, TimeUnit.SECONDS);
    }

    public void waitForTraces(final int number) throws InterruptedException, TimeoutException {
        if (!waitForTracesMax(number, 20)) {
            String msg = "Timeout waiting for " + number + " trace(s). FakeAgentHttpClient.size() == " + size();
            log.warn(msg);
            throw new TimeoutException(msg);
        }
    }
}
