package org.datadog.jenkins.plugins.datadog.apm;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Map;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

@Extension
public class DatadogTracerEnvironmentContributor extends EnvironmentContributor {

    @Override
    public void buildEnvironmentFor(@NonNull Run run, @NonNull EnvVars envs, @NonNull TaskListener listener) {
        if (run instanceof WorkflowRun) {
            // Pipelines are handled by org.datadog.jenkins.plugins.datadog.tracer.DatadogTracerStepEnvironmentContributor
            return;
        }

        Executor executor = run.getExecutor();
        if (executor == null) {
            return;
        }

        Node node = executor.getOwner().getNode();
        Map<String, String> additionalEnvVars = DatadogTracerConfigurator.INSTANCE.configure(run, node, envs, listener);
        envs.putAll(additionalEnvVars);
    }
}
