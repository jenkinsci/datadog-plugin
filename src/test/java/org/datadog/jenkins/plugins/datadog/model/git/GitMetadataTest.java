package org.datadog.jenkins.plugins.datadog.model.git;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GitMetadataTest {

  @Test
  public void mergeWithSameRepositoryURL() {
    GitMetadata a =
        new GitMetadata.Builder()
            .repositoryURL("https://example.com/repo.git")
            .defaultBranch("main")
            .branch("feature")
            .commitMetadata(
                new GitCommitMetadata.Builder()
                    .commit("commit1")
                    .authorName("author1")
                    .message("message1")
                    .build())
            .build();

    GitMetadata b =
        new GitMetadata.Builder()
            .repositoryURL("https://example.com/repo.git")
            .defaultBranch("main")
            .branch("feature")
            .commitMetadata(
                new GitCommitMetadata.Builder()
                    .commit("commit2")
                    .authorName("author2")
                    .message("message2")
                    .build())
            .build();

    GitMetadata result = GitMetadata.merge(a, b);

    assertEquals("https://example.com/repo.git", result.getRepositoryURL());
    assertEquals("main", result.getDefaultBranch());
    assertEquals("feature", result.getBranch());
    assertEquals(
        GitCommitMetadata.merge(a.getCommitMetadata(), b.getCommitMetadata()),
        result.getCommitMetadata());
  }

  @Test
  public void mergeWithDifferentRepositoryURL() {
    GitMetadata a =
        new GitMetadata.Builder()
            .repositoryURL("https://example.com/repo1.git")
            .defaultBranch("main")
            .branch("feature")
            .commitMetadata(
                new GitCommitMetadata.Builder()
                    .commit("commit1")
                    .authorName("author1")
                    .message("message1")
                    .build())
            .build();

    GitMetadata b =
        new GitMetadata.Builder()
            .repositoryURL("https://example.com/repo2.git")
            .defaultBranch("main")
            .branch("feature")
            .commitMetadata(
                new GitCommitMetadata.Builder()
                    .commit("commit2")
                    .authorName("author2")
                    .message("message2")
                    .build())
            .build();

    GitMetadata result = GitMetadata.merge(a, b);

    assertEquals("https://example.com/repo2.git", result.getRepositoryURL());
    assertEquals("main", result.getDefaultBranch());
    assertEquals("feature", result.getBranch());
    assertEquals(b.getCommitMetadata(), result.getCommitMetadata());
  }

  @Test
  public void mergeWithEmptyRepositoryURL() {
    GitMetadata a =
        new GitMetadata.Builder()
            .repositoryURL("")
            .defaultBranch("main")
            .branch("feature")
            .commitMetadata(
                new GitCommitMetadata.Builder()
                    .commit("commit1")
                    .authorName("author1")
                    .message("message1")
                    .build())
            .build();

    GitMetadata b =
        new GitMetadata.Builder()
            .repositoryURL("https://example.com/repo.git")
            .defaultBranch("main")
            .branch("feature")
            .commitMetadata(
                new GitCommitMetadata.Builder()
                    .commit("commit2")
                    .authorName("author2")
                    .message("message2")
                    .build())
            .build();

    GitMetadata result = GitMetadata.merge(a, b);

    assertEquals("https://example.com/repo.git", result.getRepositoryURL());
    assertEquals("main", result.getDefaultBranch());
    assertEquals("feature", result.getBranch());
    assertEquals(
        GitCommitMetadata.merge(a.getCommitMetadata(), b.getCommitMetadata()),
        result.getCommitMetadata());
  }

  @Test
  public void mergeWithEmptyFields() {
    GitMetadata a =
        new GitMetadata.Builder()
            .repositoryURL("https://example.com/repo.git")
            .defaultBranch("")
            .branch("")
            .commitMetadata(
                new GitCommitMetadata.Builder()
                    .commit("commit1")
                    .authorName("author1")
                    .message("message1")
                    .build())
            .build();

    GitMetadata b =
        new GitMetadata.Builder()
            .repositoryURL("https://example.com/repo.git")
            .defaultBranch("main")
            .branch("feature")
            .commitMetadata(
                new GitCommitMetadata.Builder()
                    .commit("commit2")
                    .authorName("author2")
                    .message("message2")
                    .build())
            .build();

    GitMetadata result = GitMetadata.merge(a, b);

    assertEquals("https://example.com/repo.git", result.getRepositoryURL());
    assertEquals("main", result.getDefaultBranch());
    assertEquals("feature", result.getBranch());
    assertEquals(
        GitCommitMetadata.merge(a.getCommitMetadata(), b.getCommitMetadata()),
        result.getCommitMetadata());
  }
}
