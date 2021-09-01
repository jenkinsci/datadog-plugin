package org.datadog.jenkins.plugins.datadog.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class StageDataTest {

    private static final String SAMPLE_NAME = "stage-name\nline-2";

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {StageData.builder()
                        .withName(null)
                        .withStartTimeInMicros(1000)
                        .withEndTimeInMicros(2000)
                        .build(), ""},
                {StageData.builder()
                        .withName("")
                        .withStartTimeInMicros(1000)
                        .withEndTimeInMicros(2000)
                        .build(), ""},
                {StageData.builder()
                        .withName(SAMPLE_NAME)
                        .withStartTimeInMicros(1000)
                        .withEndTimeInMicros(2000)
                        .build(), "{\"name\":\"stage-name\\nline-2\",\"duration\":1000000}"}
        });
    }


    private final StageData data;
    private final String expectedJson;

    public StageDataTest(final StageData data, final String expectedJson) {
        this.data = data;
        this.expectedJson = expectedJson;
    }

    @Test
    public void shouldReturnCorrectJson() {
        assertEquals(expectedJson, data.toJson());
    }
}