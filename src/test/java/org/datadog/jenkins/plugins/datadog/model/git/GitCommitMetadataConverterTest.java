package org.datadog.jenkins.plugins.datadog.model.git;

import static org.junit.Assert.assertEquals;

import hudson.util.XStream2;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitCommitMetadataConverterTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new GitCommitMetadata("tag", "1234567890123456789012345678901234567890", "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitMetadata(null, "1234567890123456789012345678901234567890", "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitMetadata("tag", null, "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitMetadata("tag", "1234567890123456789012345678901234567890", null, "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitMetadata("tag", "1234567890123456789012345678901234567890", "message", null, "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitMetadata("tag", "1234567890123456789012345678901234567890", "message", "authorName", null, "authorDate", "committerName", "committerEmail", "committerDate")},
                {new GitCommitMetadata("tag", "1234567890123456789012345678901234567890", "message", "authorName", "authorEmail", null, "committerName", "committerEmail", "committerDate")},
                {new GitCommitMetadata("tag", "1234567890123456789012345678901234567890", "message", "authorName", "authorEmail", "authorDate", null, "committerEmail", "committerDate")},
                {new GitCommitMetadata("tag", "1234567890123456789012345678901234567890", "message", "authorName", "authorEmail", "authorDate", "committerName", null, "committerDate")},
                {new GitCommitMetadata("tag", "1234567890123456789012345678901234567890", "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", null)},
                {new GitCommitMetadata(null, null, null, null, null, null, null, null, null)},
        });
    }

    private final GitCommitMetadata metadata;

    public GitCommitMetadataConverterTest(final GitCommitMetadata metadata) {
        this.metadata = metadata;
    }

    @Test
    public void testConverter() {
        XStream2 xStream = new XStream2(XStream2.getDefaultDriver());
        GitCommitMetadata.ConverterImpl converter = new GitCommitMetadata.ConverterImpl(xStream);
        xStream.registerConverter(converter);

        String xml = xStream.toXML(metadata);
        GitCommitMetadata deserializedMetadata = (GitCommitMetadata) xStream.fromXML(xml);
        assertEquals(metadata, deserializedMetadata);
    }

}
