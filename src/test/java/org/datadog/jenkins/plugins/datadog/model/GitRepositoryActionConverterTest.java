package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import org.datadog.jenkins.plugins.datadog.model.ActionConverterTest;
import org.datadog.jenkins.plugins.datadog.model.GitRepositoryAction;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitRepositoryActionConverterTest extends ActionConverterTest<GitRepositoryAction> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new GitRepositoryAction("repoUrl", "defaultBranch", "branch")},
                {new GitRepositoryAction(null, "defaultBranch", "branch")},
                {new GitRepositoryAction("repoUrl", null, "branch")},
                {new GitRepositoryAction("repoUrl", "defaultBranch", null)},
                {new GitRepositoryAction(null, null, "branch")},
                {new GitRepositoryAction(null, "defaultBranch", null)},
                {new GitRepositoryAction("repoUrl", null, null)},
                {new GitRepositoryAction(null, null, null)},
        });
    }

    public GitRepositoryActionConverterTest(final GitRepositoryAction action) {
        super(action);
    }

    @Override
    protected Converter getConverter(XStream xStream) {
        return new GitRepositoryAction.ConverterImpl(xStream);
    }
}
