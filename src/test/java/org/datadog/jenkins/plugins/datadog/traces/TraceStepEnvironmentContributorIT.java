package org.datadog.jenkins.plugins.datadog.traces;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import hudson.EnvVars;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class TraceStepEnvironmentContributorIT {

    @ClassRule
    public static final JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void beforeEach() throws IOException {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);
        cfg.setCiInstanceName("testService");
        cfg.setGlobalJobTags(null);
        cfg.setGlobalTags(null);
        EnvVars.masterEnvVars.remove("ENV_VAR");

        DatadogClientStub clientStub = new DatadogClientStub();
        ClientHolder.setClient(clientStub);

        Jenkins jenkins = jenkinsRule.jenkins;
        jenkins.getGlobalNodeProperties().remove(EnvironmentVariablesNodeProperty.class);
    }

    @Test
    public void testStageIdSetWhenStepInsideStage() throws Exception {
        WorkflowJob pipeline = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStageIdSet");
        try {
            pipeline.setDefinition(new CpsFlowDefinition("pipeline {\n" +
                    "  agent any\n" +
                    "  stages {\n" +
                    "    stage('MyStage') {\n" +
                    "      steps {\n" +
                    "        " + shellEcho("STAGE_ID", "DD_CUSTOM_STAGE_ID") + "\n" +
                    "        " + shellEcho("TRACE_ID", "DD_CUSTOM_TRACE_ID") + "\n" +
                    "        " + shellEcho("PARENT_ID", "DD_CUSTOM_PARENT_ID") + "\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", true));
            WorkflowRun build = jenkinsRule.buildAndAssertSuccess(pipeline);
            String log = JenkinsRule.getLog(build);
            String stageId = extractValue(log, "STAGE_ID=");
            String traceId = extractValue(log, "TRACE_ID=");
            String parentId = extractValue(log, "PARENT_ID=");
            assertNotNull("DD_CUSTOM_STAGE_ID should be set inside a stage", stageId);
            assertNotNull("DD_CUSTOM_TRACE_ID should be set inside a stage", traceId);
            assertNotNull("DD_CUSTOM_PARENT_ID should be set inside a stage", parentId);
        } finally {
            pipeline.delete();
        }
    }

    @Test
    public void testStageIdNotSetWhenStepOutsideStage() throws Exception {
        WorkflowJob pipeline = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStageIdNotSet");
        try {
            pipeline.setDefinition(new CpsFlowDefinition("node {\n" +
                    "  " + shellEcho("STAGE_ID_CHECK", "DD_CUSTOM_STAGE_ID") + "\n" +
                    "}", true));
            WorkflowRun build = jenkinsRule.buildAndAssertSuccess(pipeline);
            String log = JenkinsRule.getLog(build);
            assertNull("DD_CUSTOM_STAGE_ID should not be set outside a stage", extractValue(log, "STAGE_ID_CHECK="));
        } finally {
            pipeline.delete();
        }
    }

    @Test
    public void testDifferentStagesGetDifferentStageIds() throws Exception {
        WorkflowJob pipeline = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testDifferentStageIds");
        try {
            pipeline.setDefinition(new CpsFlowDefinition("pipeline {\n" +
                    "  agent any\n" +
                    "  stages {\n" +
                    "    stage('Stage1') {\n" +
                    "      steps {\n" +
                    "        " + shellEcho("STAGE1_ID", "DD_CUSTOM_STAGE_ID") + "\n" +
                    "      }\n" +
                    "    }\n" +
                    "    stage('Stage2') {\n" +
                    "      steps {\n" +
                    "        " + shellEcho("STAGE2_ID", "DD_CUSTOM_STAGE_ID") + "\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", true));
            WorkflowRun build = jenkinsRule.buildAndAssertSuccess(pipeline);

            String log = JenkinsRule.getLog(build);
            String stage1Id = extractValue(log, "STAGE1_ID=");
            String stage2Id = extractValue(log, "STAGE2_ID=");
            assertNotNull("DD_CUSTOM_STAGE_ID should be set for Stage1", stage1Id);
            assertNotNull("DD_CUSTOM_STAGE_ID should be set for Stage2", stage2Id);
            assertNotEquals("Stage IDs for different stages should be different", stage1Id, stage2Id);
        } finally {
            pipeline.delete();
        }
    }

    private static boolean isRunningOnWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static String shellEcho(String label, String envVar) {
        return isRunningOnWindows()
                ? "bat 'echo " + label + "=%" + envVar + "%'"
                : "sh 'echo " + label + "=$" + envVar + "'";
    }

    private static String extractValue(String log, String prefix) {
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "(\\d+)");
        Matcher matcher = pattern.matcher(log);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
