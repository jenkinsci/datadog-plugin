package org.datadog.jenkins.plugins.datadog.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hudson.model.Result;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.datadog.jenkins.plugins.datadog.listeners.DatadogBuildListener;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class PauseDurationsTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    /** Waits on an input step for 2 seconds, then carries on. */
    private static final String WAIT = "try { timeout(time: 2, unit: 'SECONDS') { input 'wait' } } catch (e) { echo 'timed out' }";
    private static final long ONE_WAIT_MIN = 1500;

    @Test
    public void scripted() throws Exception {
        WorkflowRun run = runPipeline(WAIT + "\n"
                + "stage('A') { " + WAIT + " }\n"
                + "stage('Outer') { stage('Inner') { " + WAIT + " }; " + WAIT + " }\n"
                + "stage('Par') { parallel b1: { stage('B1') { " + WAIT + " } }, b2: { " + WAIT + " } }\n"
                + "stage('NoPause') { echo 'hi' }\n"
                + "catchError(buildResult: 'UNSTABLE') { stage('Err') { " + WAIT + "; error 'boom' } }\n", Result.UNSTABLE);

        Map<String, Long> stages = stagePauses(run);
        assertWaits(run, 1, "A", stages);
        assertWaits(run, 1, "Inner", stages);
        assertWaits(run, 2, "Outer", stages);
        assertWaits(run, 1, "B1", stages);
        assertWaits(run, 2, "Par", stages);
        assertWaits(run, 0, "NoPause", stages);
        assertWaits(run, 1, "Err", stages);

        // every pause counts towards the run, including ones outside any stage, ones in an outer stage after a
        // nested stage, and ones in a parallel branch without a stage
        assertRunPause(run, 7);
    }

    @Test
    public void declarative() throws Exception {
        WorkflowRun run = runPipeline("pipeline {\n agent none\n stages {\n"
                + " stage('D1') {\n steps {\n script {\n " + WAIT + "\n }\n }\n }\n"
                + " stage('Skipped') {\n when {\n expression { false }\n }\n steps {\n echo 'never'\n }\n }\n"
                + " stage('ParD') {\n parallel {\n"
                + "  stage('P1') {\n steps {\n script {\n " + WAIT + "\n }\n }\n }\n"
                + "  stage('P2') {\n steps {\n script {\n " + WAIT + "\n }\n }\n }\n"
                + " }\n }\n"
                + " stage('Seq') {\n stages {\n stage('S1') {\n steps {\n script {\n " + WAIT + "\n }\n }\n }\n }\n }\n"
                + " stage('Fail') {\n steps {\n error 'boom'\n }\n }\n"
                + " stage('AfterFail') {\n steps {\n script {\n " + WAIT + "\n }\n }\n }\n"
                + "}\n}", Result.FAILURE);

        Map<String, Long> stages = stagePauses(run);
        assertWaits(run, 1, "D1", stages);
        assertWaits(run, 0, "Skipped", stages);
        assertWaits(run, 1, "P1", stages);
        assertWaits(run, 1, "P2", stages);
        assertWaits(run, 2, "ParD", stages);
        assertWaits(run, 1, "S1", stages);
        assertWaits(run, 1, "Seq", stages);
        assertWaits(run, 0, "Fail", stages);
        assertWaits(run, 0, "AfterFail", stages);
        assertRunPause(run, 4);
    }

    @Test
    public void interruptedWhilePausedFailure() throws Exception {
        interruptedWhilePaused(Result.FAILURE);
    }

    @Test
    public void interruptedWhilePausedNotBuilt() throws Exception {
        interruptedWhilePaused(Result.NOT_BUILT);
    }

    @Test
    public void interruptedWhilePausedAborted() throws Exception {
        interruptedWhilePaused(Result.ABORTED);
    }

    private void interruptedWhilePaused(Result result) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("stage('S') { input 'please wait' }", true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        j.waitForMessage("please wait", run);
        Thread.sleep(ONE_WAIT_MIN);
        run.getExecutor().interrupt(result);
        j.assertBuildStatus(result, j.waitForCompletion(run));

        long stagePause = stagePauses(run).get("S");
        long runPause = new DatadogBuildListener().getPauseDurationMillis(run);
        assertTrue("stage pause " + stagePause, stagePause >= ONE_WAIT_MIN);
        assertTrue("stage pause " + stagePause + " longer than the run " + run.getDuration(), stagePause <= run.getDuration());
        assertEquals(stagePause, runPause);
    }

    private static WorkflowRun runPipeline(String script, Result expected) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(script, true));
        return j.assertBuildStatus(expected, job.scheduleBuild2(0));
    }

    private static Map<String, Long> stagePauses(WorkflowRun run) {
        Map<String, Long> pauses = new HashMap<>();
        for (FlowNode node : new DepthFirstScanner().allNodes(run.getExecution())) {
            if (node instanceof BlockEndNode) {
                BlockStartNode start = ((BlockEndNode<?>) node).getStartNode();
                if (start.getAction(LabelAction.class) != null && start.getAction(ThreadNameAction.class) == null) {
                    pauses.put(start.getDisplayName(), PauseDurations.forBlock((BlockEndNode<?>) node));
                }
            }
        }
        return pauses;
    }

    /** The nodes that recorded a pause, i.e. the {@code input} steps. */
    private static List<FlowNode> pausedNodes(WorkflowRun run) {
        List<FlowNode> paused = new ArrayList<>();
        for (FlowNode node : new DepthFirstScanner().allNodes(run.getExecution())) {
            if (PauseAction.getPauseDuration(node) > 0) {
                paused.add(node);
            }
        }
        return paused;
    }

    /**
     * Asserts that the stage contains exactly {@code waits} paused nodes and that its pause is the sum of theirs.
     * Wall-clock ranges are avoided on purpose: on a loaded machine a 2s timeout can take much longer to fire.
     */
    private static void assertWaits(WorkflowRun run, int waits, String stage, Map<String, Long> stagePauses) {
        long expected = 0;
        int count = 0;
        for (FlowNode node : pausedNodes(run)) {
            for (BlockStartNode enclosing : node.getEnclosingBlocks()) {
                if (stage.equals(enclosing.getDisplayName()) && enclosing.getAction(LabelAction.class) != null
                        && enclosing.getAction(ThreadNameAction.class) == null) {
                    expected += PauseAction.getPauseDuration(node);
                    count++;
                    break;
                }
            }
        }
        assertEquals("paused nodes in " + stage, waits, count);
        assertEquals("pause of " + stage, Long.valueOf(expected), stagePauses.get(stage));
    }

    private static void assertRunPause(WorkflowRun run, int waits) {
        List<FlowNode> paused = pausedNodes(run);
        assertEquals("paused nodes in the run", waits, paused.size());
        long expected = 0;
        for (FlowNode node : paused) {
            expected += PauseAction.getPauseDuration(node);
        }
        assertEquals(expected, new DatadogBuildListener().getPauseDurationMillis(run));
    }
}
