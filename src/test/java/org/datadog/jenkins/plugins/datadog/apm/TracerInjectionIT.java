package org.datadog.jenkins.plugins.datadog.apm;

import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.*;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentHost;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentLogCollectionPort;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentPort;
import static org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration.DatadogAgentConfigurationDescriptor.getDefaultAgentTraceCollectionPort;
import static org.junit.Assume.assumeTrue;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.slaves.DumbSlave;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.configuration.DatadogAgentConfiguration;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;

public class TracerInjectionIT {

    // "mvn" script does not quote MAVEN_OPTS properly, this is outside our control
    @ClassRule
    public static TestRule noSpaceInTmpDirs = FlagRule.systemProperty("jenkins.test.noSpaceInTmpDirs", "true");

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private static DumbSlave agentNode;

    @BeforeClass
    public static void suiteSetUp() throws Exception {
        DatadogGlobalConfiguration datadogConfig = DatadogUtilities.getDatadogGlobalDescriptor();
        // There is no agent and the injected tracers will fail to actually send anything,
        // but for the purposes of this test this is enough, since it is only asserted that the tracer initialisation was reported in the logs
        datadogConfig.setDatadogClientConfiguration(new DatadogAgentConfiguration(
                getDefaultAgentHost(), getDefaultAgentPort(), getDefaultAgentLogCollectionPort(), getDefaultAgentTraceCollectionPort()));

        agentNode = jenkinsRule.createOnlineSlave();
    }

    @AfterClass
    public static void suiteTearDown() throws Exception {
        jenkinsRule.disconnectSlave(agentNode);
    }

    @Test
    public void testTracerInjectionInFreestyleProject() throws Exception {
        FreeStyleProject project = givenFreestyleProject();
        try {
            givenProjectIsAMavenBuild(project);
            givenTracerInjectionEnabled(project);
            FreeStyleBuild build = whenRunningBuild(project);
            thenTracerIsInjected(build);
        } finally {
            project.delete();
        }
    }

    @Test
    public void testTracerInjectionInFreestyleProjectExecutedOnAgentNode() throws Exception {
        FreeStyleProject project = givenFreestyleProject();
        try {
            givenProjectIsBuiltOnAgentNode(project);
            givenProjectIsAMavenBuild(project);
            givenTracerInjectionEnabled(project);
            FreeStyleBuild build = whenRunningBuild(project);
            thenTracerIsInjected(build);
        } finally {
            project.delete();
        }
    }

    @Test
    public void testTracerInjectionInPipeline() throws Exception {
        WorkflowJob pipeline = givenPipelineProjectBuiltOnMaster();
        try {
            givenPipelineIsAMavenBuild(pipeline, false);
            givenTracerInjectionEnabled(pipeline);
            WorkflowRun build = whenRunningBuild(pipeline);
            thenTracerIsInjected(build);
        } finally {
            pipeline.delete();
        }
    }

    @Test
    public void testTracerInjectionInPipelineExecutedOnAgentNode() throws Exception {
        WorkflowJob pipeline = givenPipelineProjectBuiltOnAgentNode();
        try {
            givenPipelineIsAMavenBuild(pipeline, true);
            givenTracerInjectionEnabled(pipeline);
            WorkflowRun build = whenRunningBuild(pipeline);
            thenTracerIsInjected(build);
        } finally {
            pipeline.delete();
        }
    }

    @Test
    public void testTracerInjectionViaPipelineStep() throws Exception {
        WorkflowJob pipeline = givenPipelineProjectWithTracerEnabledStep();
        try {
            givenPipelineIsAMavenBuild(pipeline, false);
            WorkflowRun build = whenRunningBuild(pipeline);
            thenTracerIsInjected(build);
        } finally {
            pipeline.delete();
        }
    }

    @Test
    public void testDisabledTracerInjectionViaPipelineStep() throws Exception {
        WorkflowJob pipeline = givenPipelineProjectWithTracerDisabledStep();
        try {
            givenPipelineIsAMavenBuild(pipeline, false);
            WorkflowRun build = whenRunningBuild(pipeline);
            thenTracerIsNotInjected(build);
        } finally {
            pipeline.delete();
        }
    }

    @Test
    public void testPipelineStepWithMissingTracerConfig() throws Exception {
        WorkflowJob pipeline = givenPipelineProjectWithTracerConfigMissing();
        try {
            givenPipelineIsAMavenBuild(pipeline, false);
            WorkflowRun build = whenRunningBuild(pipeline);
            thenTracerIsNotInjected(build);
        } finally {
            pipeline.delete();
        }
    }

    @Test
    public void testTracerInjectionViaPipelineStepInSingleStage() throws Exception {
        assumeTrue(!isRunningOnWindows()); // the feature is platform-independent, but the test is not
        WorkflowJob pipeline = givenPipelineProjectWithTracerEnabledStepInOneStage();
        try {
            WorkflowRun build = whenRunningBuild(pipeline);
            jenkinsRule.assertLogContains("Stage 1 has no tracer", build);
            jenkinsRule.assertLogContains("Stage 2 has tracer", build);
            jenkinsRule.assertLogContains("Stage 3 has no tracer", build);
        } finally {
            pipeline.delete();
        }
    }

    private FreeStyleProject givenFreestyleProject() throws IOException {
        return jenkinsRule.createFreeStyleProject("freestyleProject");
    }

    private WorkflowJob givenPipelineProjectBuiltOnMaster() throws Exception {
        return givenPipelineProject(false);
    }

    private WorkflowJob givenPipelineProjectBuiltOnAgentNode() throws Exception {
        return givenPipelineProject(true);
    }

    private WorkflowJob givenPipelineProject(boolean builtOnAgentNode) throws Exception {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("AGENT_LABEL", builtOnAgentNode ? agentNode.getSelfLabel().getName() : "built-in");
        replacements.put("PIPELINE_STEPS", getMavenCommand());

        String pipelineDefinition = buildPipelineDefinition("test-maven-pipeline.txt", replacements);
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineProject");
        job.setDefinition(new CpsFlowDefinition(pipelineDefinition, true));
        return job;
    }

    private WorkflowJob givenPipelineProjectWithTracerEnabledStep() throws Exception {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("DATADOG_STEP_SETTINGS", "testOptimization: [ enabled: true, languages: [\"JAVA\"], additionalVariables: [\"my-var\": \"value\"] ]");
        replacements.put("PIPELINE_STEPS", getMavenCommand());

        String pipelineDefinition = buildPipelineDefinition("test-maven-pipeline-with-datadog-step.txt", replacements);
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineProject");
        job.setDefinition(new CpsFlowDefinition(pipelineDefinition, true));
        return job;
    }

    private WorkflowJob givenPipelineProjectWithTracerEnabledStepInOneStage() throws Exception {
        String pipelineDefinition = buildPipelineDefinition("test-multistage-maven-pipeline-with-datadog-step.txt", Collections.emptyMap());
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineProject");
        job.setDefinition(new CpsFlowDefinition(pipelineDefinition, true));
        return job;
    }

    private WorkflowJob givenPipelineProjectWithTracerDisabledStep() throws Exception {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("DATADOG_STEP_SETTINGS", "testOptimization: [ enabled: false, languages: [\"JAVA\"], additionalVariables: [\"my-var\": \"value\"] ]");
        replacements.put("PIPELINE_STEPS", getMavenCommand());

        String pipelineDefinition = buildPipelineDefinition("test-maven-pipeline-with-datadog-step.txt", replacements);
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineProject");
        job.setDefinition(new CpsFlowDefinition(pipelineDefinition, true));
        return job;
    }

    private WorkflowJob givenPipelineProjectWithTracerConfigMissing() throws Exception {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("DATADOG_STEP_SETTINGS", "");
        replacements.put("PIPELINE_STEPS", getMavenCommand());

        String pipelineDefinition = buildPipelineDefinition("test-maven-pipeline-with-datadog-step.txt", replacements);
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineProject");
        job.setDefinition(new CpsFlowDefinition(pipelineDefinition, true));
        return job;
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$([A-Z_]+)");

    @NotNull
    private static String buildPipelineDefinition(String pipelineName, Map<String, String> replacements) throws IOException {
        String pipelineDefinition;
        try (InputStream is = TracerInjectionIT.class.getResourceAsStream(pipelineName)) {
            StringBuffer pipelineBuilder = new StringBuffer();
            String pipelineTemplate = IOUtils.toString(is, Charset.defaultCharset());
            Matcher m = PLACEHOLDER_PATTERN.matcher(pipelineTemplate);
            while (m.find()) {
                String placeholder = m.group(1);
                m.appendReplacement(pipelineBuilder, replacements.get(placeholder));
            }
            m.appendTail(pipelineBuilder);
            pipelineDefinition = pipelineBuilder.toString();
        }
        return pipelineDefinition;
    }

    private String getMavenCommand() {
        return isRunningOnWindows()
                ? "bat \"./mvnw.cmd clean\""
                : "sh \"./mvnw clean\"";
    }

    private boolean isRunningOnWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private void givenProjectIsBuiltOnAgentNode(FreeStyleProject project) throws Exception {
        project.setAssignedNode(agentNode);
    }

    private void givenProjectIsAMavenBuild(FreeStyleProject project) throws Exception {
        copyMavenFilesToWorkspace(agentNode.getSelfLabel().equals(project.getAssignedLabel()), project);
        project.getBuildersList().add(new Shell("./mvnw clean"));
    }

    private void givenPipelineIsAMavenBuild(WorkflowJob pipeline, boolean builtOnAgent) throws Exception {
        copyMavenFilesToWorkspace(builtOnAgent, pipeline);
    }

    private void copyMavenFilesToWorkspace(boolean builtOnAgent, TopLevelItem item) throws Exception {
        FilePath projectWorkspace;
        if (builtOnAgent) {
            projectWorkspace = agentNode.getWorkspaceFor(item);
        } else {
            projectWorkspace = jenkinsRule.jenkins.getWorkspaceFor(item);
        }

        URL mavenProjectUrl = TracerInjectionIT.class.getResource("/org/datadog/jenkins/plugins/datadog/apm/test-maven-project");
        File mavenProject = new File(mavenProjectUrl.getFile());
        FilePath mavenProjectPath = new FilePath(mavenProject);
        mavenProjectPath.copyRecursiveTo(projectWorkspace);
    }

    private void givenTracerInjectionEnabled(Job job) throws IOException {
        DatadogTracerJobProperty<FreeStyleProject> traceInjectionConfig = new DatadogTracerJobProperty<>(
                true,
                null,
                Collections.singletonList(TracerLanguage.JAVA),
                Collections.singletonMap("DD_CIVISIBILITY_ENABLED", "false")
        );
        job.addProperty(traceInjectionConfig);
    }

    private FreeStyleBuild whenRunningBuild(FreeStyleProject project) throws Exception {
        return jenkinsRule.buildAndAssertSuccess(project);
    }

    private WorkflowRun whenRunningBuild(WorkflowJob pipeline) throws Exception {
        return jenkinsRule.buildAndAssertSuccess(pipeline);
    }

    private void thenTracerIsInjected(FreeStyleBuild build) throws IOException {
        jenkinsRule.assertLogContains("DATADOG TRACER CONFIGURATION", build);
    }

    private void thenTracerIsInjected(WorkflowRun build) throws IOException {
        jenkinsRule.assertLogContains("DATADOG TRACER CONFIGURATION", build);
    }

    private void thenTracerIsNotInjected(WorkflowRun build) throws IOException {
        jenkinsRule.assertLogNotContains("DATADOG TRACER CONFIGURATION", build);
    }
}
