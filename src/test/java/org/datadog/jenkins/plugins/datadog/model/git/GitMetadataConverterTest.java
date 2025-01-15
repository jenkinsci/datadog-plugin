package org.datadog.jenkins.plugins.datadog.model.git;

import static org.junit.Assert.assertEquals;

import hudson.util.XStream2;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitMetadataConverterTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        GitCommitMetadata commitMetadata = new GitCommitMetadata("tag", "commit", "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate");
        return Arrays.asList(new Object[][]{
                {new GitMetadata("http://repo.url", "defaultBranch", "branch", commitMetadata)},
                {new GitMetadata(null, "defaultBranch", "branch", commitMetadata)},
                {new GitMetadata("http://repo.url", null, "branch", commitMetadata)},
                {new GitMetadata("http://repo.url", "defaultBranch", null, commitMetadata)},
                {new GitMetadata("http://repo.url", "defaultBranch", "branch", GitCommitMetadata.EMPTY)},
        });
    }

    private final GitMetadata metadata;

    public GitMetadataConverterTest(final GitMetadata metadata) {
        this.metadata = metadata;
    }

    @Test
    public void testConverter() {
        XStream2 xStream = new XStream2(XStream2.getDefaultDriver());
        GitCommitMetadata.ConverterImpl converter = new GitCommitMetadata.ConverterImpl(xStream);
        xStream.registerConverter(converter);

        String xml = xStream.toXML(metadata);
        GitMetadata deserializedMetadata = (GitMetadata) xStream.fromXML(xml);
        assertEquals(metadata, deserializedMetadata);
    }

}
