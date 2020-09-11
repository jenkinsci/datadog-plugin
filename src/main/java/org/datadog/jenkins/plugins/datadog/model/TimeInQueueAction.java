package org.datadog.jenkins.plugins.datadog.model;

import hudson.model.InvisibleAction;

public class TimeInQueueAction extends InvisibleAction {

    private final long secondsInQueue;

    public TimeInQueueAction(final long secondsInQueue) {
        this.secondsInQueue = secondsInQueue;
    }

    public long getSecondsInQueue() {
        return secondsInQueue;
    }
}
