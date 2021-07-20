package org.datadog.jenkins.plugins.datadog.transport;

import org.datadog.jenkins.plugins.datadog.traces.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeAgentHttpClient implements AgentHttpClient {
    private static final Logger log = LoggerFactory.getLogger(FakeAgentHttpClient.class);

    private final List<TraceSpan> spans = new CopyOnWriteArrayList<>();
    private final List<CountDownLatch> latches = new ArrayList();
    private final AtomicInteger traceCount = new AtomicInteger();

    @Override
    public void send(TraceSpan span) {
        System.out.println("--- send: " + span.getOperationName());
        this.traceCount.incrementAndGet();
        synchronized (this.latches) {
            spans.add(span);
            for(final CountDownLatch latch : latches) {
                if(spans.size() >= latch.getCount()) {
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
            if (spans.size() >= number) {
                return true;
            }
            latches.add(latch);
        }
        return latch.await(seconds, TimeUnit.SECONDS);
    }

    public void waitForTraces(final int number) throws InterruptedException, TimeoutException {
        if (!waitForTracesMax(number, 20)) {
            String msg = "Timeout waiting for " + number + " trace(s). FakeAgentHttpClient.size() == " + spans.size();
            log.warn(msg);
            throw new TimeoutException(msg);
        }
    }

    public List<TraceSpan> getSpans() {
        Collections.sort(spans, new Comparator<TraceSpan>() {
            @Override
            public int compare(TraceSpan span1, TraceSpan span2) {
                if(span1.getStartNano() < span2.getStartNano()){
                    return -1;
                } else if (span1.getStartNano() > span2.getStartNano()) {
                    return 1;
                }
                return 0;
            }
        });
        return spans;
    }

    @Override
    public void stop() {
        // N/A
    }

    @Override
    public void close() {
        // N/A
    }
}
