package org.datadog.jenkins.plugins.datadog.model.git;

import static org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter.ignoreOldData;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.io.Serializable;
import java.util.Objects;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class GitMetadata implements Serializable {

  public static final GitMetadata EMPTY = new GitMetadata.Builder().commitMetadata(GitCommitMetadata.EMPTY).build();

  private final String repositoryURL;
  private final String defaultBranch;
  private final String branch;
  private final GitCommitMetadata commitMetadata;

  public GitMetadata(String repositoryURL, String defaultBranch, String branch, GitCommitMetadata commitMetadata) {
    this.repositoryURL = repositoryURL;
    this.defaultBranch = defaultBranch;
    this.branch = branch;
    this.commitMetadata = commitMetadata;
  }

  public String getRepositoryURL() {
    return repositoryURL;
  }

  public String getDefaultBranch() {
    return defaultBranch;
  }

  public String getBranch() {
    return branch;
  }

  public GitCommitMetadata getCommitMetadata() {
    return commitMetadata;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GitMetadata that = (GitMetadata) o;
    return Objects.equals(repositoryURL, that.repositoryURL)
        && Objects.equals(defaultBranch, that.defaultBranch)
        && Objects.equals(branch, that.branch)
        && Objects.equals(commitMetadata, that.commitMetadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(repositoryURL, defaultBranch, branch, commitMetadata);
  }

  @Override
  public String toString() {
    return "GitMetadata{" +
        ", repositoryURL='" + repositoryURL + '\'' +
        ", defaultBranch='" + defaultBranch + '\'' +
        ", branch='" + branch + '\'' +
        ", commitMetadata=" + commitMetadata +
        '}';
  }

  public static final class Builder implements Serializable {
    private String repositoryURL;
    private String defaultBranch;
    private String branch;
    private GitCommitMetadata commitMetadata = GitCommitMetadata.EMPTY;

    public Builder repositoryURL(String repositoryURL) {
      this.repositoryURL = repositoryURL;
      return this;
    }

    public Builder defaultBranch(String defaultBranch) {
      this.defaultBranch = defaultBranch;
      return this;
    }

    public Builder branch(String branch) {
      this.branch = branch;
      return this;
    }

    public Builder commitMetadata(GitCommitMetadata commitMetadata) {
      this.commitMetadata = commitMetadata;
      return this;
    }

    public GitMetadata build() {
      return new GitMetadata(repositoryURL, defaultBranch, branch, commitMetadata);
    }
  }

  public static final class ConverterImpl extends DatadogConverter<GitMetadata> {
    public ConverterImpl(XStream xs) {
      super(ignoreOldData(), new ConverterV1(xs));
    }
  }

  public static final class ConverterV1 extends VersionedConverter<GitMetadata> {

    private static final int VERSION = 1;

    private final DatadogConverter<GitCommitMetadata> commitMetadataConverter;

    public ConverterV1(XStream xs) {
      super(VERSION);
      this.commitMetadataConverter = new GitCommitMetadata.ConverterImpl(xs);
    }

    @Override
    public void marshal(GitMetadata action, HierarchicalStreamWriter writer, MarshallingContext context) {
      if (action.repositoryURL != null) {
        writeField("repositoryURL", action.repositoryURL, writer, context);
      }
      if (action.defaultBranch != null) {
        writeField("defaultBranch", action.defaultBranch, writer, context);
      }
      if (action.branch != null) {
        writeField("branch", action.branch, writer, context);
      }
      if (action.commitMetadata != null) {
        writer.startNode("commitMetadata");
        commitMetadataConverter.marshal(action.commitMetadata, writer, context);
        writer.endNode();
      }
    }

    @Override
    public GitMetadata unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
      Builder builder = new Builder();
      while (reader.hasMoreChildren()) {
        reader.moveDown();
        String fieldName = reader.getNodeName();
        switch (fieldName) {
          case "repositoryURL":
            builder.repositoryURL((String) context.convertAnother(null, String.class));
            break;
          case "defaultBranch":
            builder.defaultBranch((String) context.convertAnother(null, String.class));
            break;
          case "branch":
            builder.branch((String) context.convertAnother(null, String.class));
            break;
          case "commitMetadata":
            builder.commitMetadata(commitMetadataConverter.unmarshal(reader, context));
            break;
          default:
            // unknown tag, could be something serialized by a different version of the plugin
            break;
        }
        reader.moveUp();
      }
      return builder.build();
    }
  }

  public static GitMetadata merge(GitMetadata a, GitMetadata b) {
    if (StringUtils.isNotBlank(a.repositoryURL) && StringUtils.isNotBlank(b.repositoryURL) && !Objects.equals(a.repositoryURL, b.repositoryURL)) {
      // repository URLs are different, so we cannot merge the data
      return b;
    }
    GitMetadata.Builder builder = new GitMetadata.Builder();
    merge(builder, a);
    merge(builder, b);
    builder.commitMetadata(GitCommitMetadata.merge(a.commitMetadata, b.commitMetadata));
    return builder.build();
  }

  private static void merge(GitMetadata.Builder builder, GitMetadata metadata) {
    if (!StringUtils.isBlank(metadata.repositoryURL)) {
      builder.repositoryURL(metadata.repositoryURL);
    }
    if (!StringUtils.isBlank(metadata.defaultBranch)) {
      builder.defaultBranch(metadata.defaultBranch);
    }
    if (!StringUtils.isBlank(metadata.branch)) {
      builder.branch(metadata.branch);
    }
  }
}
