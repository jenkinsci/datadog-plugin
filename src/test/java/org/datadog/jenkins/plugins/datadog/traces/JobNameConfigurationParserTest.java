package org.datadog.jenkins.plugins.datadog.traces;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RunWith(Parameterized.class)
public class JobNameConfigurationParserTest {

    private static final Map<String, String> EMPTY_MAP = Collections.EMPTY_MAP;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        final Map<String, String> sampleMap = new HashMap<>();
        sampleMap.put("key1", "value1");
        sampleMap.put("key2", "value2");

        final Object[][] data = new Object[][] {
                {null, null, EMPTY_MAP},
                {null, "master", EMPTY_MAP},
                {"", "master", EMPTY_MAP},
                {"jobName", null, EMPTY_MAP},
                {"jobName", "", EMPTY_MAP},
                {"jobName", "master", EMPTY_MAP},
                {"jobName/master", "master", EMPTY_MAP},
                {"jobName/another", "master", EMPTY_MAP},
                {"jobName/another/branch", "another/branch", EMPTY_MAP},
                {"jobName/another%2Fbranch", "another/branch", EMPTY_MAP},
                {"jobName/KEY1=VALUE1,KEY2=VALUE2", "master", sampleMap},
                {"jobName/KEY1=VALUE1,KEY2=VALUE2/master", "master", sampleMap},
                {"jobName/KEY1=VALUE1,KEY2=VALUE2/another-branch", "master", sampleMap}
        };


        return Arrays.asList(data);
    }

    private final String rawJobName;
    private final String gitBranch;
    private final Map<String, String> expectedConfigs;

    public JobNameConfigurationParserTest(final String rawJobName, final String gitBranch, final Map<String, String> expectedConfigs) {
        this.rawJobName = rawJobName;
        this.gitBranch = gitBranch;
        this.expectedConfigs = expectedConfigs;
    }

    @Test
    public void shouldReturnCorrectJobName() {
        assertEquals(this.expectedConfigs, JobNameConfigurationParser.getConfigurations(this.rawJobName, this.gitBranch));
    }

}