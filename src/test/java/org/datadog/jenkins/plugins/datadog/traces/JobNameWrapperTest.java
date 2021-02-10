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
public class JobNameWrapperTest {

    private static final Map<String, String> EMPTY_MAP = Collections.EMPTY_MAP;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        final Map<String, String> sampleMap = new HashMap<>();
        sampleMap.put("key1", "value1");
        sampleMap.put("key2", "value2");

        final Object[][] data = new Object[][] {
                {null, null, null, EMPTY_MAP},
                {null, "master", null, EMPTY_MAP},
                {"", "master", "", EMPTY_MAP},
                {"jobName", null, "jobName", EMPTY_MAP},
                {"jobName", "", "jobName", EMPTY_MAP},
                {"jobName", "master", "jobName", EMPTY_MAP},
                {"jobName/master", "master", "jobName", EMPTY_MAP},
                {"jobName/another", "master", "jobName/another", EMPTY_MAP},
                {"jobName/another/branch", "another/branch", "jobName", EMPTY_MAP},
                {"jobName/another%2Fbranch", "another/branch", "jobName", EMPTY_MAP},
                {"jobName/KEY1=VALUE1,KEY2=VALUE2", "master", "jobName", sampleMap},
                {"jobName/KEY1=VALUE1,KEY2=VALUE2/master", "master", "jobName", sampleMap},
                {"jobName/KEY1=VALUE1,KEY2=VALUE2/another-branch", "master", "jobName", sampleMap}
        };


        return Arrays.asList(data);
    }

    private final String rawJobName;
    private final String gitBranch;
    private final String expectedJobName;
    private final Map<String, String> expectedConfigs;

    public JobNameWrapperTest(final String rawJobName, final String gitBranch, final String expectedJobName, final Map<String, String> expectedConfigs) {
        this.rawJobName = rawJobName;
        this.gitBranch = gitBranch;
        this.expectedJobName = expectedJobName;
        this.expectedConfigs = expectedConfigs;
    }

    @Test
    public void shouldReturnCorrectJobName() {
        final JobNameWrapper sut = new JobNameWrapper(this.rawJobName, this.gitBranch);
        assertEquals(this.expectedJobName, sut.getTraceJobName());
        assertEquals(this.expectedConfigs, sut.getConfigurations());
    }

}