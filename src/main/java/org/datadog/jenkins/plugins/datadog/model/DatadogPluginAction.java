package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;
import java.io.Serializable;
import org.jenkinsci.plugins.workflow.actions.PersistentAction;

/**
 * Marker interface for all actions that are added by the plugin
 */
public abstract class DatadogPluginAction extends InvisibleAction implements PersistentAction, Serializable {
}
