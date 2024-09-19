package org.datadog.jenkins.plugins.datadog.util;

@FunctionalInterface
public interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;
}
