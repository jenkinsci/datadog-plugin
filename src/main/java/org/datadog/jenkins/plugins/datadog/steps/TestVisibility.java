package org.datadog.jenkins.plugins.datadog.steps;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.datadog.jenkins.plugins.datadog.tracer.TracerLanguage;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class TestVisibility implements Serializable {
    private boolean enabled;
    private String serviceName;
    private Collection<TracerLanguage> languages = Collections.emptyList();
    private Map<String, String> additionalVariables = Collections.emptyMap();

    @DataBoundConstructor
    public TestVisibility() {
    }

    public boolean getEnabled() {
        return enabled;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    @DataBoundSetter
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Collection<TracerLanguage> getLanguages() {
        return languages;
    }

    @DataBoundSetter
    public void setLanguages(Collection<TracerLanguage> languages) {
        this.languages = languages;
    }

    public Map<String, String> getAdditionalVariables() {
        return additionalVariables;
    }

    @DataBoundSetter
    public void setAdditionalVariables(Map<String, String> additionalVariables) {
        this.additionalVariables = additionalVariables;
    }
}
