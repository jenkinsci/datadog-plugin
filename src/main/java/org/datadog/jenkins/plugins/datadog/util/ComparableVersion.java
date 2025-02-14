package org.datadog.jenkins.plugins.datadog.util;

import java.util.Arrays;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

public class ComparableVersion implements Comparable<ComparableVersion> {

  private final int[] tokens;

  public ComparableVersion(int[] tokens) {
    this.tokens = tokens;
  }

  @Override
  public int compareTo(@Nonnull ComparableVersion o) {
    for (int i = 0; i < tokens.length && i < o.tokens.length; i++) {
      int c = Integer.compare(tokens[i], o.tokens[i]);
      if (c != 0) {
        return c;
      }
    }
    return Integer.compare(tokens.length, o.tokens.length);
  }

  public static ComparableVersion parse(@Nonnull String s) {
    int[] tokens = Arrays.stream(s.split("\\.")).mapToInt(Integer::parseInt).toArray();
    return new ComparableVersion(tokens);
  }

  @Override
  public String toString() {
    return Arrays.stream(tokens).mapToObj(String::valueOf).collect(Collectors.joining(","));
  }
}

