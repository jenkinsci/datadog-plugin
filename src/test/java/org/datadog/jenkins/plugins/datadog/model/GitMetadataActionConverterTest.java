package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.datadog.jenkins.plugins.datadog.model.git.GitCommitMetadata;
import org.datadog.jenkins.plugins.datadog.model.git.GitMetadata;
import org.datadog.jenkins.plugins.datadog.model.git.Source;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitMetadataActionConverterTest extends ActionConverterTest<GitMetadataAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        GitCommitMetadata commitMetadata = new GitCommitMetadata("tag", "commit", "message", "authorName", "authorEmail", "authorDate", "committerName", "committerEmail", "committerDate");
        GitMetadata metadata = new GitMetadata("repoUrl", "defaultBranch", "branch", commitMetadata);

        GitCommitMetadata commitMetadataB = new GitCommitMetadata("tagB", "commitB", "messageB", "authorNameB", "authorEmailB", "authorDateB", "committerNameB", "committerEmailB", "committerDateB");
        GitMetadata metadataB = new GitMetadata("repoUrlB", "defaultBranchB", "branchB", commitMetadataB);

        Map<Source, GitMetadata> metadataMap = new HashMap<>();
        metadataMap.put(Source.GIT_CLIENT, metadata);
        metadataMap.put(Source.USER_SUPPLIED_ENV_VARS, metadataB);

        return Arrays.asList(
            new Object[][] {
              {new GitMetadataAction(Collections.singletonMap(Source.GIT_CLIENT, metadata))},
              {new GitMetadataAction(metadataMap)},
              {new GitMetadataAction(Collections.emptyMap())},
            });
    }

    public GitMetadataActionConverterTest(final GitMetadataAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        xStream.registerConverter(new GitCommitMetadata.ConverterImpl(xStream));
        xStream.registerConverter(new GitMetadata.ConverterImpl(xStream));
        return new GitMetadataAction.ConverterImpl(xStream);
    }
}
