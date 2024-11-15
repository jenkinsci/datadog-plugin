package org.datadog.jenkins.plugins.datadog.clients;

import java.util.Collection;
import java.util.function.Function;

public interface JsonPayloadSender<T> {
  void send(Collection<T> payloads) throws Exception;
}
