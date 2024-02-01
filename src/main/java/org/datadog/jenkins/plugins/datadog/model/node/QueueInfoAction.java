package org.datadog.jenkins.plugins.datadog.model.node;

import org.datadog.jenkins.plugins.datadog.model.DatadogPluginAction;

/**
 * A marker interface for enqueue and dequeue actions.
 * Allows to replace an enqueue action with a dequeue action in one call, avoiding writing to disk twice.
 */
public class QueueInfoAction extends DatadogPluginAction  {
}
