package org.datadog.jenkins.plugins.datadog.clients;

import java.util.Collection;

public interface JsonPayloadSender<T> {
  void send(Collection<T> payloads) throws Exception;
}
