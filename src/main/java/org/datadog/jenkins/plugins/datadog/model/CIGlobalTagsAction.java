package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CIGlobalTagsAction extends InvisibleAction implements Serializable {

    private final Map<String, String> tags;

    public CIGlobalTagsAction(final Map<String, String> tags) {
        this.tags = tags != null ? tags : new HashMap<>();
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void putAll(Map<String, String> tags) {
        this.tags.putAll(tags);
    }
}
