package org.datadog.jenkins.plugins.datadog.traces;

import hudson.model.InvisibleAction;
import org.datadog.jenkins.plugins.datadog.model.BuildData;

import java.io.Serializable;

/**
 * Keeps build webhook propagation
 */
public class BuildWebhookAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final BuildData buildData;

    public BuildWebhookAction(final BuildData buildData){
       this.buildData = buildData;
    }

    public BuildData getBuildData() {
        return buildData;
    }

}
