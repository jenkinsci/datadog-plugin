package org.datadog.jenkins.plugins.datadog.tracer;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

public class DatadogTracerJobProperty<T extends Job<?, ?>> extends JobProperty<T> {

    private static final String DISPLAY_NAME = "Enable Datadog Test Visibility";

    @NonNull
    private DatadogTracerJobPropertyInfo info = new DatadogTracerJobPropertyInfo();

    private boolean on;

    public DatadogTracerJobProperty() {}

    @DataBoundConstructor
    public DatadogTracerJobProperty(DatadogTracerJobPropertyInfo info) {
        this.info = info;
    }

    @DataBoundSetter
    public void setOn(boolean on) {
        this.on = on;
    }

    public boolean isOn() {
        return on;
    }

    @DataBoundSetter
    public void setInfo(DatadogTracerJobPropertyInfo info) {
        this.info = info;
    }

    @NonNull
    public DatadogTracerJobPropertyInfo getInfo() {
        return info;
    }

    @Extension
    public static final class DatadogTracerJobPropertyDescriptor extends JobPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public DatadogTracerJobProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData.optBoolean("on")) {
                return (DatadogTracerJobProperty) super.newInstance(req, formData);
            }
            return null;
        }
    }
}
