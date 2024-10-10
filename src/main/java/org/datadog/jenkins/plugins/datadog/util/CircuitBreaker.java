package org.datadog.jenkins.plugins.datadog.util;

import javax.annotation.concurrent.GuardedBy;
import java.util.function.Consumer;

public class CircuitBreaker<T> {

    public static final int DEFAULT_MIN_HEALTH_CHECK_DELAY_MILLIS = 1000;
    public static final int DEFAULT_MAX_HEALTH_CHECK_DELAY_MILLIS = 60000;
    public static final double DEFAULT_DELAY_FACTOR = 2.0;

    private final ThrowingConsumer<T> action;
    private final Consumer<T> fallback;
    private final Consumer<Exception> errorHandler;
    private final long minHealthCheckDelayMillis;
    private final long maxHealthCheckDelayMillis;
    private final double delayFactor;

    @GuardedBy("this")
    private boolean healthy;
    @GuardedBy("this")
    private long healthCheckDelayMillis;
    @GuardedBy("this")
    private long healthCheckAt;

    public CircuitBreaker(ThrowingConsumer<T> action, Consumer<T> fallback, Consumer<Exception> errorHandler) {
        this(action, fallback, errorHandler, DEFAULT_MIN_HEALTH_CHECK_DELAY_MILLIS, DEFAULT_MAX_HEALTH_CHECK_DELAY_MILLIS, DEFAULT_DELAY_FACTOR);
    }

    public CircuitBreaker(ThrowingConsumer<T> action,
                          Consumer<T> fallback,
                          Consumer<Exception> errorHandler,
                          long minHealthCheckDelayMillis,
                          long maxHealthCheckDelayMillis,
                          double delayFactor) {
        this.action = action;
        this.fallback = fallback;
        this.errorHandler = errorHandler;
        this.minHealthCheckDelayMillis = minHealthCheckDelayMillis;
        this.maxHealthCheckDelayMillis = maxHealthCheckDelayMillis;
        this.delayFactor = delayFactor;
        synchronized (this) {
            this.healthy = true;
            this.healthCheckDelayMillis = minHealthCheckDelayMillis;
        }
    }

    public synchronized void accept(T t) {
        // normal flow
        if (healthy) {
            try {
                action.accept(t);
            } catch (Exception e) {
                errorHandler.accept(e);
                healthy = false;
                healthCheckAt = System.currentTimeMillis() + healthCheckDelayMillis;
                fallback.accept(t);
            }

        // try to recover
        } else if (System.currentTimeMillis() >= healthCheckAt) {
            try {
                action.accept(t);
                healthy = true;
                healthCheckDelayMillis = minHealthCheckDelayMillis;
            } catch (Exception e) {
                errorHandler.accept(e);
                healthCheckDelayMillis = Math.min(Math.round(healthCheckDelayMillis  * delayFactor), maxHealthCheckDelayMillis);
                healthCheckAt = System.currentTimeMillis() + healthCheckDelayMillis;
                fallback.accept(t);
            }

        // "broken" flow
        } else {
            fallback.accept(t);
        }
    }
}
