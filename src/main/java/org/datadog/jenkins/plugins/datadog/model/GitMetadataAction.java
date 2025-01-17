package org.datadog.jenkins.plugins.datadog.model;

import static org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter.ignoreOldData;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.model.git.GitMetadata;
import org.datadog.jenkins.plugins.datadog.model.git.Source;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

public class GitMetadataAction extends DatadogPluginAction {

  private final Map<Source, GitMetadata> metadataBySource;

  public GitMetadataAction() {
    this.metadataBySource = new HashMap<>();
  }

  GitMetadataAction(Map<Source, GitMetadata> metadataBySource) {
    this.metadataBySource = metadataBySource;
  }

  public synchronized void addMetadata(Source metadataSource, GitMetadata metadata) {
    metadataBySource.merge(metadataSource, metadata, GitMetadata::merge);
  }

  public synchronized GitMetadata getMetadata() {
    GitMetadata metadata = GitMetadata.EMPTY;
    for (Source source : Source.values()) {
      GitMetadata sourceMetadata = metadataBySource.get(source);
      if (sourceMetadata != null) {
        metadata = GitMetadata.merge(metadata, sourceMetadata);
      }
    }
    return metadata;
  }

  public GitMetadata getPipelineDefinitionMetadata() {
    return metadataBySource.getOrDefault(Source.GIT_CLIENT_PIPELINE_DEFINITION, GitMetadata.EMPTY);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GitMetadataAction that = (GitMetadataAction) o;
    return Objects.equals(metadataBySource, that.metadataBySource);
  }

  @Override
  public int hashCode() {
    return Objects.hash(metadataBySource);
  }

  @Override
  public String toString() {
    return "GitMetadataAction{" +
        "metadata=" + metadataBySource +
        '}';
  }

  public static final class ConverterImpl extends DatadogConverter<GitMetadataAction> {
    public ConverterImpl(XStream xs) {
      super(ignoreOldData(), new ConverterV1(xs));
    }
  }

  public static final class ConverterV1 extends VersionedConverter<GitMetadataAction> {

    private static final int VERSION = 1;

    public ConverterV1(XStream xs) {
      super(VERSION);
    }

    @Override
    public void marshal(GitMetadataAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
      writeField("metadataBySource", action.metadataBySource, writer, context);
    }

    @Override
    public GitMetadataAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
      Map<Source, GitMetadata> metadataBySource = readField(reader, context, Map.class);
      return new GitMetadataAction(metadataBySource);
    }
  }
}
