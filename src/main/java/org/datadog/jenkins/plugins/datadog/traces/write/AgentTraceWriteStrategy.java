package org.datadog.jenkins.plugins.datadog.traces.write;

import hudson.model.Run;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.clients.DatadogAgentClient;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.BuildPipelineNode;

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
    private volatile boolean evpProxySupported = false;
    private volatile long lastEvpProxyCheckTimeMs = 0L;

    public AgentTraceWriteStrategy(TraceWriteStrategy evpProxyStrategy, TraceWriteStrategy apmStrategy, Supplier<Boolean> checkEvpProxySupport) {
        this.evpProxyStrategy = evpProxyStrategy;
        this.apmStrategy = apmStrategy;
        this.checkEvpProxySupport = checkEvpProxySupport;
    }

    @Nullable
    @Override
    public JSONObject serialize(BuildData buildData, Run<?, ?> run) {
        return getCurrentStrategy().serialize(buildData, run);
    }

    @Nonnull
    @Override
    public JSONObject serialize(BuildPipelineNode node, Run<?, ?> run) throws IOException, InterruptedException  {
        return getCurrentStrategy().serialize(node, run);
    }

    @Override
    public void send(List<JSONObject> spans) {
        // we have to check serialized spans to know where to send them,
        // because the serialization strategy might've changed in between serialize() and send()
        if (isWebhook(spans)) {
            evpProxyStrategy.send(spans);
        } else {
            apmStrategy.send(spans);
        }
    }

    private boolean isWebhook(List<JSONObject> spans) {
        if (spans.isEmpty()) {
            return false;
        }
        JSONObject span = spans.iterator().next();
        return span.get("level") != null;
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