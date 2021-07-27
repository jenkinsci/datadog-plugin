package org.datadog.jenkins.plugins.datadog.transport;

import java.util.List;

public interface PayloadMapper<T extends List<? extends PayloadMessage>> {

    byte[] map(final T list);

    String contentType();
}
