package org.datadog.jenkins.plugins.datadog.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.sf.json.JSONObject;
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.clients.ClientHolder;
import org.datadog.jenkins.plugins.datadog.clients.DatadogClientStub;
import org.datadog.jenkins.plugins.datadog.traces.write.Payload;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategy;
import org.datadog.jenkins.plugins.datadog.traces.write.TraceWriteStrategyImpl;
import org.datadog.jenkins.plugins.datadog.traces.write.Track;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * A checkout whose HEAD commit cannot be resolved (HEAD points to a commit object that is missing from the repository).
 * Previously this left a null commit metadata in the run's GitMetadataAction, after which every BuildData creation,
 * subsequent checkout event and the build finalization failed with a NullPointerException.
 */
public class UnresolvableGitHeadIT {

    private static final String REPO_URL = "https://github.com/example/unresolvable-head.git";

    @ClassRule
    public static final JenkinsRule j = new JenkinsRule();

    @Rule
    public final LoggerRule logs = new LoggerRule().record("org.datadog.jenkins.plugins.datadog", Level.SEVERE).capture(1000);

    private final List<JSONObject> sentPayloads = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        DatadogGlobalConfiguration cfg = DatadogUtilities.getDatadogGlobalDescriptor();
        cfg.setEnableCiVisibility(true);
        // real serialization (webhook track) instead of the stub's, captures what would be sent to Datadog
        TraceWriteStrategy realStrategy = new TraceWriteStrategyImpl(Track.WEBHOOK, payloads -> {
            for (Payload p : payloads) {
                sentPayloads.add(p.getJson());
            }
        });
        ClientHolder.setClient(new DatadogClientStub() {
            @Override
            public TraceWriteStrategy createTraceWriteStrategy() {
                return realStrategy;
            }
        });
    }

    @Test
    public void buildWithUnresolvableHeadIsReportedWithoutErrors() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("unresolvable-head");
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                try {
                    FilePath workspace = build.getWorkspace();
                    createRepoWithMissingHeadCommit(new File(workspace.getRemote()));
                    // what Jenkins does after an SCM checkout; fired twice, as pipelines with several checkouts do
                    DatadogSCMListener scmListener = ExtensionList.lookupSingleton(DatadogSCMListener.class);
                    scmListener.onCheckout(build, new GitSCM(REPO_URL), workspace, listener, null, null);
                    scmListener.onCheckout(build, new GitSCM(REPO_URL), workspace, listener, null, null);
                    return true;
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        j.buildAndAssertSuccess(project);

        JSONObject pipeline = awaitFinishedPipelinePayload();

        List<String> severe = logs.getMessages();
        severe.forEach(m -> System.out.println("[SEVERE] " + m));
        List<String> npes = severe.stream().filter(m -> m.contains("NullPointerException")).collect(Collectors.toList());
        assertTrue("Unexpected NullPointerExceptions: " + npes, npes.isEmpty());

        assertEquals("unresolvable-head", pipeline.getString("name"));
        assertEquals(REPO_URL, pipeline.getJSONObject("git").getString("repository_url"));
    }

    private JSONObject awaitFinishedPipelinePayload() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            for (JSONObject p : sentPayloads) {
                if (!"running".equals(p.optString("status"))) {
                    return p;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No finished pipeline payload was sent. Payloads: " + sentPayloads
                + "\nSEVERE logs: " + logs.getMessages());
    }

    private static void createRepoWithMissingHeadCommit(File dir) throws Exception {
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.init().setDirectory(dir).setInitialBranch("main").call()) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "origin", "url", REPO_URL);
            // do not depend on the user's global git config (e.g. gpg.format=ssh is not supported by older JGit)
            config.setString("gpg", null, "format", "openpgp");
            config.setBoolean("commit", null, "gpgsign", false);
            config.save();

            Files.write(new File(dir, "README.md").toPath(), "hello".getBytes(StandardCharsets.UTF_8));
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("initial").setAuthor("test", "test@example.com").setCommitter("test", "test@example.com").setSign(false).call();

            // HEAD still points to the commit, but the commit object is gone: HEAD cannot be resolved to a commit
            ObjectId head = git.getRepository().resolve("HEAD");
            String name = head.name();
            File object = new File(dir, ".git/objects/" + name.substring(0, 2) + "/" + name.substring(2));
            assertTrue("could not delete " + object, object.delete());
        }
    }
}
