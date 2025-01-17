package org.datadog.jenkins.plugins.datadog.model.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.jupiter.api.Test;

public class GitCommitMetadataTest {

  @Test
  public void testMergeWithDifferentCommits() {
    GitCommitMetadata metadata1 = new GitCommitMetadata.Builder().commit("commit1").build();
    GitCommitMetadata metadata2 = new GitCommitMetadata.Builder().commit("commit2").build();
    GitCommitMetadata result = GitCommitMetadata.merge(metadata1, metadata2);
    assertEquals(metadata2, result);
  }

  @Test
  public void testMergeWithSameCommits() {
    GitCommitMetadata metadata1 = new GitCommitMetadata.Builder().commit("commit1").message("message1").build();
    GitCommitMetadata metadata2 = new GitCommitMetadata.Builder().commit("commit1").authorName("author2").build();
    GitCommitMetadata result = GitCommitMetadata.merge(metadata1, metadata2);
    assertEquals("commit1", result.getCommit());
    assertEquals("message1", result.getMessage());
    assertEquals("author2", result.getAuthorName());
  }

  @Test
  public void testMergeWithEmptyMetadata() {
    GitCommitMetadata metadata1 = new GitCommitMetadata.Builder().commit("commit1").build();
    GitCommitMetadata result = GitCommitMetadata.merge(metadata1, GitCommitMetadata.EMPTY);
    assertEquals(metadata1, result);
  }

  @Test
  public void testMergeWithNullFields() {
    GitCommitMetadata metadata1 = new GitCommitMetadata.Builder().commit("commit1").build();
    GitCommitMetadata metadata2 = new GitCommitMetadata.Builder().commit("commit1").authorName(null).build();
    GitCommitMetadata result = GitCommitMetadata.merge(metadata1, metadata2);
    assertEquals("commit1", result.getCommit());
    assertNull(result.getAuthorName());
  }


}
