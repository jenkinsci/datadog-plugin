package org.datadog.jenkins.plugins.datadog.apm;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.steps.TestOptimization;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

public class DatadogTracerJobProperty<T extends Job<?, ?>> extends JobProperty<T> {

    private static final String DISPLAY_NAME = "Enable Datadog Test Optimization";

    private final boolean on;
    private final String serviceName;
    private final Collection<TracerLanguage> languages;
    private final Map<String, String> additionalVariables;

    public DatadogTracerJobProperty(boolean on, String serviceName, @Nonnull Collection<TracerLanguage> languages, Map<String, String> additionalVariables) {
        this.on = on;
        this.serviceName = serviceName;
        this.languages = languages;
        this.additionalVariables = additionalVariables;
    }

    public boolean isOn() {
        return on;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Nonnull
    public Collection<TracerLanguage> getLanguages() {
        return languages;
    }

    public Map<String, String> getAdditionalVariables() {
        return additionalVariables;
    }

    public List<DatadogTracerEnvironmentProperty> getAdditionalVariablesAsList() {
        List<DatadogTracerEnvironmentProperty> list = new ArrayList<>();
        for (Map.Entry<String, String> e : additionalVariables.entrySet()) {
            list.add(new DatadogTracerEnvironmentProperty(e.getKey(), e.getValue()));
        }
        return list;
    }

    public TestOptimization getTestOptimization() {
        return new TestOptimization(on, serviceName, languages, additionalVariables);
    }

    @Extension
    public static final class DatadogTracerJobPropertyDescriptor extends JobPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public DatadogTracerJobProperty<?> newInstance(StaplerRequest2 req, JSONObject formData) {
            if (!formData.optBoolean("on")) {
                return null;
            }

            String serviceName = formData.getString("serviceName");

            Set<TracerLanguage> languages = EnumSet.noneOf(TracerLanguage.class);
            JSONObject languagesJson = (JSONObject) formData.get("languages");
            for (Map.Entry<String, Boolean> e : (Set<Map.Entry<String, Boolean>>) languagesJson.entrySet()) {
                TracerLanguage language = TracerLanguage.valueOf(e.getKey());
                if (e.getValue()) {
                    languages.add(language);
                }
            }

            Map<String, String> additionalVariables = new HashMap<>();
            List<DatadogTracerEnvironmentProperty> additionalVariablesList = req.bindJSONToList(DatadogTracerEnvironmentProperty.class, formData.get("additionalVariable"));
            for (DatadogTracerEnvironmentProperty additionalVariable : additionalVariablesList) {
                additionalVariables.put(additionalVariable.getName(), additionalVariable.getValue());
            }

            return new DatadogTracerJobProperty<>(true, serviceName, languages, additionalVariables);
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
