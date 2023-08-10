package org.datadog.jenkins.plugins.datadog.tracer;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import java.io.Serializable;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class DatadogTracerJobProperty<T extends Job<?, ?>> extends JobProperty<T> {

    private static final String DISPLAY_NAME = "Enable Datadog Test Visibility";

    private final boolean on;
    private final String serviceName;
    private final List<DatadogTracerEnvironmentProperty> additionalVariables;

    public DatadogTracerJobProperty(boolean on, String serviceName, List<DatadogTracerEnvironmentProperty> additionalVariables) {
        this.on = on;
        this.serviceName = serviceName;
        this.additionalVariables = additionalVariables;
    }

    public boolean isOn() {
        return on;
    }

    public String getServiceName() {
        return serviceName;
    }

    public List<DatadogTracerEnvironmentProperty> getAdditionalVariables() {
        return additionalVariables;
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
        public DatadogTracerJobProperty newInstance(StaplerRequest req, JSONObject formData) {
            if (!formData.optBoolean("on")) {
                return null;
            }

            String serviceName = formData.getString("serviceName");
            List<DatadogTracerEnvironmentProperty> additionalVariables = req.bindJSONToList(DatadogTracerEnvironmentProperty.class, formData.get("additionalVariable"));
            return new DatadogTracerJobProperty(true, serviceName, additionalVariables);
        }
    }

    public static class DatadogTracerEnvironmentProperty implements Serializable {
        private final String name;
        private final String value;

        @DataBoundConstructor
        public DatadogTracerEnvironmentProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @CheckForNull
        public String getName() {
            return name;
        }

        @NonNull
        public String getValue() {
            return value;
        }
    }
}
