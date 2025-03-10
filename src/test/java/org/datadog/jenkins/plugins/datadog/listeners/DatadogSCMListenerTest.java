package org.datadog.jenkins.plugins.datadog.listeners;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.FilePath;
import hudson.model.Run;
import java.io.File;
import org.junit.Test;

public class DatadogSCMListenerTest {

  @Test
  public void testIsCommonSharedLibraryClone() {
    assertTrue(DatadogSCMListener.isCommonSharedLibraryClone(filePath("/var/jenkins_home/workspace/my-pipeline@libs/1234567890")));
    assertFalse(DatadogSCMListener.isCommonSharedLibraryClone(filePath("/var/jenkins_home/workspace/my-pipeline-libs/1234567890")));
  }

  @Test
  public void testIsFreshSharedLibraryClone() {
    assertTrue(DatadogSCMListener.isFreshSharedLibraryClone(
        run("/var/jenkins_home/jobs/my-pipeline/builds/123"),
        filePath("/var/jenkins_home/jobs/my-pipeline/builds/123/libs/1234567890/root")));
    assertFalse(DatadogSCMListener.isFreshSharedLibraryClone(
        run("/var/jenkins_home/jobs/my-pipeline/builds/123"),
        filePath("/var/jenkins_home/workspace/my-pipeline@libs/1234567890")));
    assertFalse(DatadogSCMListener.isFreshSharedLibraryClone(
        run("/var/jenkins_home/jobs/my-pipeline/builds/123"),
        filePath("C:/jenkins_home/workspace/my-pipeline@libs/1234567890")));
  }

  private static Run run(String rootDir) {
    Run run = mock(Run.class);
    when(run.getRootDir()).thenReturn(new File(rootDir));
    return run;
  }

  private static FilePath filePath(String path) {
    return new FilePath(new File(path));
  }

}
