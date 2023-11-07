package org.datadog.jenkins.plugins.datadog.tracer;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Map;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepEnvironmentContributor;

@Extension
public class DatadogTracerStepEnvironmentContributor extends StepEnvironmentContributor {

    @Override
    public void buildEnvironmentFor(StepContext stepContext, @NonNull EnvVars envs, @NonNull TaskListener listener) throws IOException, InterruptedException {
        Run<?, ?> run = stepContext.get(Run.class);
        Node node = stepContext.get(Node.class);
        if (run != null && node != null) {
            Map<String, String> additionalEnvVars = DatadogTracerConfigurator.INSTANCE.configure(run, node, envs);
            envs.putAll(additionalEnvVars);
        }
    }

}
