package org.datadog.jenkins.plugins.datadog.util.json;

import static org.junit.Assert.assertEquals;

import org.datadog.jenkins.plugins.datadog.model.StageData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RunWith(Parameterized.class)
public class JsonUtilsTest {

    private static final String SAMPLE_NAME = "stage-name";

    private static final StageData STAGE = StageData.builder()
            .withName(SAMPLE_NAME)
            .withStartTimeInMicros(1000)
            .withEndTimeInMicros(2000)
            .build();

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {null, "[]"},
                {Collections.EMPTY_LIST, "[]"},
                {Collections.singletonList(STAGE), "[{\"name\":\"stage-name\",\"duration\":1000000}]"},
                {Arrays.asList(STAGE, STAGE), "[{\"name\":\"stage-name\",\"duration\":1000000},{\"name\":\"stage-name\",\"duration\":1000000}]"}
        });
    }

    private final List<ToJson> items;
    private final String expectedJson;

    public JsonUtilsTest(List<ToJson> items, String expectedJson) {
        this.items = items;
        this.expectedJson = expectedJson;
    }

    @Test
    public void shouldReturnCorrectJson() {
        assertEquals(expectedJson, JsonUtils.toJson(items));
    }
}