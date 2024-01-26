package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.datadog.jenkins.plugins.datadog.clients.DatadogAgentClient;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Trace write strategy that can dynamically switch from using APM track to using EVP Proxy.
 * The switch will happen if an older agent (one that doesn't support EVP Proxy) is replaced with a newer agent.
 */
public class AgentTraceWriteStrategy implements TraceWriteStrategy {

    private static final Logger logger = Logger.getLogger(AgentTraceWriteStrategy.class.getName());

    /**
     * How often to check the /info endpoint in case the Agent got updated.
     */
    private static final long EVP_PROXY_SUPPORT_TIME_BETWEEN_CHECKS_MS = TimeUnit.HOURS.toMillis(1);

    private final TraceWriteStrategy evpProxyStrategy;
    private final TraceWriteStrategy apmStrategy;
    private final Supplier<Boolean> checkEvpProxySupport;
    /**
     * Whether the Agent supports EVP Proxy.
     * <p>
     * This value may change from {@code false} to {@code true} if the Agent that this Jenkins talks to gets updated
     * (the Agent's support for EVP proxy is checked periodically).
     * <p>
     * We don't handle agent downgrades, so {@code true} to {@code false} change is not possible.
     */
    private volatile boolean evpProxySupported = false;
    private volatile long lastEvpProxyCheckTimeMs = 0L;

    public AgentTraceWriteStrategy(TraceWriteStrategy evpProxyStrategy, TraceWriteStrategy apmStrategy, Supplier<Boolean> checkEvpProxySupport) {
        this.evpProxyStrategy = evpProxyStrategy;
        this.apmStrategy = apmStrategy;
        this.checkEvpProxySupport = checkEvpProxySupport;
    }

    @Override
    public Span createSpan(BuildData buildData, Run<?, ?> run) {
        return getCurrentStrategy().createSpan(buildData, run);
    }

    @Nonnull
    @Override
    public Collection<Span> createSpan(FlowNode flowNode, Run<?, ?> run) {
        return getCurrentStrategy().createSpan(flowNode, run);
    }

    @Override
    public void send(Collection<Span> spans) {
        // we have to check the track for every span,
        // because the serialization strategy might've changed in between serialize() and send()
        Map<Track, List<Span>> spansByTrack = spans.stream().collect(Collectors.groupingBy(Span::getTrack));
        for (Map.Entry<Track, List<Span>> e : spansByTrack.entrySet()) {
            Track track = e.getKey();
            List<Span> trackSpans = e.getValue();

            if (track == Track.WEBHOOK) {
                evpProxyStrategy.send(trackSpans);
            } else if (track == Track.APM) {
                apmStrategy.send(trackSpans);
            } else {
                throw new IllegalArgumentException("Unexpected track value: " + track);
            }
        }
    }

    private TraceWriteStrategy getCurrentStrategy() {
        if (isEvpProxySupported()) {
            return evpProxyStrategy;
        } else {
            return apmStrategy;
        }
    }

    private boolean isEvpProxySupported() {
        if (evpProxySupported) {
            return true; // Once we have seen an Agent that supports EVP Proxy, we never check again.
        }
        if (System.currentTimeMillis() < (lastEvpProxyCheckTimeMs + EVP_PROXY_SUPPORT_TIME_BETWEEN_CHECKS_MS)) {
            return evpProxySupported; // Wait at least 1 hour between checks, return the cached value
        }
        synchronized (DatadogAgentClient.class) {
            if (!evpProxySupported) {
                evpProxySupported = checkEvpProxySupport.get();
                lastEvpProxyCheckTimeMs = System.currentTimeMillis();
                if (evpProxySupported) {
                    logger.info("EVP Proxy is supported by the Agent. We will not check again until the next boot.");
                } else {
                    logger.info("The Agent doesn't support EVP Proxy, falling back to APM for CI Visibility. Requires Agent v6.42+ or 7.42+.");
                }
            }
        }
        return evpProxySupported;
    }
}
