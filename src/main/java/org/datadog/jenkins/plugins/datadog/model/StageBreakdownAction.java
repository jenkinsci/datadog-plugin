package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the Stage breakdown related information.
 */
public class StageBreakdownAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, StageData> stageDataByName;

    public StageBreakdownAction() {
        this.stageDataByName = new HashMap<>();
    }

    public Map<String, StageData> getStageDataByName() {
        return stageDataByName;
    }

    public void put(String name, StageData stageData) {
        this.stageDataByName.put(name, stageData);
    }
}
