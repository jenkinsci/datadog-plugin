package org.datadog.jenkins.plugins.datadog.util;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ComparableVersionTest {

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        {"1.0", "1.0", 0},
        {"1.0", "2.0", -1},
        {"2.0", "1.0", 1},
        {"1.0", "1.0.1", -1},
        {"1.0.1", "1.0", 1},
        {"1.0.0", "1.1.0", -1},
        {"1.1.0", "1.0.0", 1},
        {"1.0.23", "1.12.0", -1},
        {"1.21.0", "1.0.22", 1},
    });
  }

  private final ComparableVersion a;
  private final ComparableVersion b;
  private final int expectedComparisonResult;

  public ComparableVersionTest(String a, String b, int expectedComparisonResult) {
    this.a = ComparableVersion.parse(a);
    this.b = ComparableVersion.parse(b);
    this.expectedComparisonResult = expectedComparisonResult;
  }

  @Test
  public void testVersionsComparison() {
    assertEquals(expectedComparisonResult, a.compareTo(b));
  }

}
