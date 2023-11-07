package org.datadog.jenkins.plugins.datadog.tracer;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.slaves.DumbSlave;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;

public class TracerInjectionIT {

    // "mvn" script does not quote MAVEN_OPTS properly, this is outside of our control
    @ClassRule
    public static TestRule noSpaceInTmpDirs = FlagRule.systemProperty("jenkins.test.noSpaceInTmpDirs", "true");

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private static DumbSlave agentNode;

    @BeforeClass
    public static void suiteSetUp() throws Exception {
        DatadogGlobalConfiguration datadogConfig = DatadogUtilities.getDatadogGlobalDescriptor();
        datadogConfig.setReportWith("DSD");

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
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipelineProject");
        String definition = "pipeline {\n" +
                "    agent {\n" +
                "      label '" + (builtOnAgentNode ? agentNode.getSelfLabel().getName() : "built-in") + "'\n" +
                "    }\n" +
                "    stages {\n" +
                "        stage('test'){\n" +
                "            steps {\n" +
                "                " + getMavenCommand() + "\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";
        job.setDefinition(new CpsFlowDefinition(definition, true));
        return job;
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

        URL mavenProjectUrl = TracerInjectionIT.class.getResource("/org/datadog/jenkins/plugins/datadog/tracer/test-maven-project");
        File mavenProject = new File(mavenProjectUrl.getFile());
        FilePath mavenProjectPath = new FilePath(mavenProject);
        mavenProjectPath.copyRecursiveTo(projectWorkspace);
    }

    private void givenTracerInjectionEnabled(Job job) throws IOException {
        DatadogTracerJobProperty<FreeStyleProject> traceInjectionConfig = new DatadogTracerJobProperty<>(
                true,
                "integration-test-service-name",
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
}
