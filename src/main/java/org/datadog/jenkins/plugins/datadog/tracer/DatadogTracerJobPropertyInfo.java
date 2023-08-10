package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class DatadogTracerJobPropertyInfo implements Describable<DatadogTracerJobPropertyInfo> {

    private String serviceName;

    public DatadogTracerJobPropertyInfo() {}

    @DataBoundConstructor
    public DatadogTracerJobPropertyInfo(String serviceName) {
        this.serviceName = serviceName;
    }

    @DataBoundSetter
    public DatadogTracerJobPropertyInfo setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public Descriptor<DatadogTracerJobPropertyInfo> getDescriptor() {
        return Jenkins.get().getDescriptorByType(DatadogTracerJobPropertyInfoDescriptor.class);
    }

    @Extension
    public static class DatadogTracerJobPropertyInfoDescriptor extends Descriptor<DatadogTracerJobPropertyInfo> {
        @Override
        public String getDisplayName() {
            return "DatadogTracerJobPropertyInfo";
        }
    }
}
