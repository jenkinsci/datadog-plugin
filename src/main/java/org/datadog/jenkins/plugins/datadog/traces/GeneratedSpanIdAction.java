package org.datadog.jenkins.plugins.datadog.traces;

import datadog.trace.api.DDId;
import hudson.model.InvisibleAction;

import java.io.Serializable;

public class GeneratedSpanIdAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String spanId;

    public GeneratedSpanIdAction(DDId spanId) {
        this.spanId = spanId.toString();
    }

    public DDId getDDSpanId() {
        return DDId.from(spanId);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("GeneratedSpanIdAction{");
        sb.append("spanId=").append(spanId);
        sb.append('}');
        return sb.toString();
    }
}
