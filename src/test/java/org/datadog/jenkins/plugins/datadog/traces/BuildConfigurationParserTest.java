package org.datadog.jenkins.plugins.datadog.traces;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.matrix.MatrixConfiguration;
import hudson.model.Run;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BuildConfigurationParserTest {

    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        final Map<String, String> sampleMap = new HashMap<>();
        sampleMap.put("key1", "value1");
        sampleMap.put("key2", "value2");

        final Object[][] data = new Object[][]{
                {null, EMPTY_MAP},
                {"", EMPTY_MAP},
                {"jobName", EMPTY_MAP},
                {"jobName/KEY1=VALUE1,KEY2=VALUE2", sampleMap},
        };

        return Arrays.asList(data);
    }

    private final String rawJobName;
    private final Map<String, String> expectedConfigs;

    public BuildConfigurationParserTest(final String rawJobName, final Map<String, String> expectedConfigs) {
        this.rawJobName = rawJobName;
        this.expectedConfigs = expectedConfigs;
    }

    @Test
    public void shouldReturnCorrectJobName() {
        MatrixConfiguration job = mock(MatrixConfiguration.class);
        when(job.getFullName()).thenReturn(this.rawJobName);

        Run run = mock(Run.class);
        when(run.getParent()).thenReturn(job);

        assertEquals(this.expectedConfigs, BuildConfigurationParser.parseConfigurations(run));
    }

}