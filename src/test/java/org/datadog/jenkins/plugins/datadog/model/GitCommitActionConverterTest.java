package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitCommitActionConverterTest extends ActionConverterTest<GitCommitAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new GitCommitAction("tag", "commit", "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitAction(null, "commit", "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitAction("tag", null, "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitAction("tag", "commit", null, "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitAction("tag", "commit", "message", null, "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitAction("tag", "commit", "message", "authorName", null, "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitAction("tag", "commit", "message", "authorName", "authorEmail", null, "committerName", "committerEmail", "committerDate")},
                {new GitCommitAction("tag", "commit", "message", "authorName", "authorEmail", "authorDate", null, "committerEmail", "committerDate")},
                {new GitCommitAction("tag", "commit", "message", "authorName", "authorEmail", "authorDate", "committerName", null, "committerDate")},
                {new GitCommitAction("tag", "commit", "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", null)},
                {new GitCommitAction(null, null, null, null, null, null, null, null, null)},
        });
    }

    public GitCommitActionConverterTest(final GitCommitAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        return new GitCommitAction.ConverterImpl(xStream);
    }
}
