package org.datadog.jenkins.plugins.datadog.traces;

public final class TracerConstants {

    private TracerConstants(){}

    // EnvVar keys to support custom spans.
    // These env vars will be used by datadog-ci CLI to continue the trace.
    public static final String TRACE_ID_ENVVAR_KEY = "DD_CUSTOM_TRACE_ID";
    public static final String SPAN_ID_ENVVAR_KEY = "DD_CUSTOM_PARENT_ID";

}
