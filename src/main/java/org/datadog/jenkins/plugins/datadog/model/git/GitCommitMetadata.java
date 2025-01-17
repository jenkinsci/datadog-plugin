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

public class GitCommitMetadata implements Serializable {

  public static final GitCommitMetadata EMPTY = new Builder().build();

  private final String tag;
  private final String commit;
  private final String message;
  private final String authorName;
  private final String authorEmail;
  private final String authorDate;
  private final String committerName;
  private final String committerEmail;
  private final String committerDate;

  public GitCommitMetadata(String tag, String commit, String message, String authorName, String authorEmail, String authorDate, String committerName, String committerEmail, String committerDate) {
    this.tag = tag;
    this.commit = commit;
    this.message = message;
    this.authorName = authorName;
    this.authorEmail = authorEmail;
    this.authorDate = authorDate;
    this.committerName = committerName;
    this.committerEmail = committerEmail;
    this.committerDate = committerDate;
  }

  public String getTag() {
    return tag;
  }

  public String getCommit() {
    return commit;
  }

  public String getMessage() {
    return message;
  }

  public String getAuthorName() {
    return authorName;
  }

  public String getAuthorEmail() {
    return authorEmail;
  }

  public String getAuthorDate() {
    return authorDate;
  }

  public String getCommitterName() {
    return committerName;
  }

  public String getCommitterEmail() {
    return committerEmail;
  }

  public String getCommitterDate() {
    return committerDate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GitCommitMetadata that = (GitCommitMetadata) o;
    return Objects.equals(tag, that.tag)
        && Objects.equals(commit, that.commit)
        && Objects.equals(message, that.message)
        && Objects.equals(authorName, that.authorName)
        && Objects.equals(authorEmail, that.authorEmail)
        && Objects.equals(authorDate, that.authorDate)
        && Objects.equals(committerName, that.committerName)
        && Objects.equals(committerEmail, that.committerEmail)
        && Objects.equals(committerDate, that.committerDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tag, commit, message, authorName, authorEmail, authorDate, committerName, committerEmail, committerDate);
  }

  @Override
  public String toString() {
    return "GitCommitMetadata{" +
        "tag='" + tag + '\'' +
        ", commit='" + commit + '\'' +
        ", message='" + message + '\'' +
        ", authorName='" + authorName + '\'' +
        ", authorEmail='" + authorEmail + '\'' +
        ", authorDate='" + authorDate + '\'' +
        ", committerName='" + committerName + '\'' +
        ", committerEmail='" + committerEmail + '\'' +
        ", committerDate='" + committerDate + '\'' +
        '}';
  }

  public static final class Builder implements Serializable {
    private String tag;
    private String commit;
    private String message;
    private String authorName;
    private String authorEmail;
    private String authorDate;
    private String committerName;
    private String committerEmail;
    private String committerDate;

    public Builder tag(String tag) {
      this.tag = tag;
      return this;
    }

    public Builder commit(String commit) {
      this.commit = commit;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder authorName(String authorName) {
      this.authorName = authorName;
      return this;
    }

    public Builder authorEmail(String authorEmail) {
      this.authorEmail = authorEmail;
      return this;
    }

    public Builder authorDate(String authorDate) {
      this.authorDate = authorDate;
      return this;
    }

    public Builder committerName(String committerName) {
      this.committerName = committerName;
      return this;
    }

    public Builder committerEmail(String committerEmail) {
      this.committerEmail = committerEmail;
      return this;
    }

    public Builder committerDate(String committerDate) {
      this.committerDate = committerDate;
      return this;
    }

    public GitCommitMetadata build() {
      return new GitCommitMetadata(tag, commit, message, authorName, authorEmail, authorDate, committerName, committerEmail, committerDate);
    }
  }

  public static final class ConverterImpl extends DatadogConverter<GitCommitMetadata> {
    public ConverterImpl(XStream xs) {
      super(ignoreOldData(), new ConverterV1());
    }
  }

  public static final class ConverterV1 extends VersionedConverter<GitCommitMetadata> {

    private static final int VERSION = 1;

    public ConverterV1() {
      super(VERSION);
    }

    @Override
    public void marshal(GitCommitMetadata commitMetadata, HierarchicalStreamWriter writer, MarshallingContext context) {
      if (commitMetadata.tag != null) {
        writeField("tag", commitMetadata.tag, writer, context);
      }
      if (commitMetadata.commit != null) {
        writeField("commit", commitMetadata.commit, writer, context);
      }
      if (commitMetadata.message != null) {
        writeField("message", commitMetadata.message, writer, context);
      }
      if (commitMetadata.authorName != null) {
        writeField("authorName", commitMetadata.authorName, writer, context);
      }
      if (commitMetadata.authorEmail != null) {
        writeField("authorEmail", commitMetadata.authorEmail, writer, context);
      }
      if (commitMetadata.authorDate != null) {
        writeField("authorDate", commitMetadata.authorDate, writer, context);
      }
      if (commitMetadata.committerName != null) {
        writeField("committerName", commitMetadata.committerName, writer, context);
      }
      if (commitMetadata.committerEmail != null) {
        writeField("committerEmail", commitMetadata.committerEmail, writer, context);
      }
      if (commitMetadata.committerDate != null) {
        writeField("committerDate", commitMetadata.committerDate, writer, context);
      }
    }

    @Override
    public GitCommitMetadata unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
      Builder builder = new Builder();
      while (reader.hasMoreChildren()) {
        reader.moveDown();
        String fieldName = reader.getNodeName();
        switch (fieldName) {
          case "tag":
            builder.tag = (String) context.convertAnother(null, String.class);
            break;
          case "commit":
            builder.commit = (String) context.convertAnother(null, String.class);
            break;
          case "message":
            builder.message = (String) context.convertAnother(null, String.class);
            break;
          case "authorName":
            builder.authorName = (String) context.convertAnother(null, String.class);
            break;
          case "authorEmail":
            builder.authorEmail = (String) context.convertAnother(null, String.class);
            break;
          case "authorDate":
            builder.authorDate = (String) context.convertAnother(null, String.class);
            break;
          case "committerName":
            builder.committerName = (String) context.convertAnother(null, String.class);
            break;
          case "committerEmail":
            builder.committerEmail = (String) context.convertAnother(null, String.class);
            break;
          case "committerDate":
            builder.committerDate = (String) context.convertAnother(null, String.class);
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

  public static GitCommitMetadata merge(GitCommitMetadata a, GitCommitMetadata b) {
    if (StringUtils.isNotBlank(a.commit) && StringUtils.isNotBlank(b.commit) && !Objects.equals(a.commit, b.commit)) {
      // commits are different, so we cannot merge the data
      return b;
    }
    Builder builder = new Builder();
    merge(builder, a);
    merge(builder, b);
    return builder.build();
  }

  private static void merge(GitCommitMetadata.Builder builder, GitCommitMetadata metadata) {
    if (!StringUtils.isBlank(metadata.tag)) {
      builder.tag(metadata.tag);
    }
    if (!StringUtils.isBlank(metadata.commit)) {
      builder.commit(metadata.commit);
    }
    if (!StringUtils.isBlank(metadata.message)) {
      builder.message(metadata.message);
    }
    if (!StringUtils.isBlank(metadata.authorName)) {
      builder.authorName(metadata.authorName);
    }
    if (!StringUtils.isBlank(metadata.authorDate)) {
      builder.authorDate(metadata.authorDate);
    }
    if (!StringUtils.isBlank(metadata.authorEmail)) {
      builder.authorEmail(metadata.authorEmail);
    }
    if (!StringUtils.isBlank(metadata.committerName)) {
      builder.committerName(metadata.committerName);
    }
    if (!StringUtils.isBlank(metadata.committerDate)) {
      builder.committerDate(metadata.committerDate);
    }
    if (!StringUtils.isBlank(metadata.committerEmail)) {
      builder.committerEmail(metadata.committerEmail);
    }
  }
}
