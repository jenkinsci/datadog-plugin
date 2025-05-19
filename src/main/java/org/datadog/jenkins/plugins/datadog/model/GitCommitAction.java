package org.datadog.jenkins.plugins.datadog.model;

import static org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter.ignoreOldData;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import java.io.Serial;
import java.util.Objects;
import org.datadog.jenkins.plugins.datadog.util.conversion.DatadogConverter;
import org.datadog.jenkins.plugins.datadog.util.conversion.VersionedConverter;

/**
 * @deprecated use {@link GitMetadataAction}
 * This action cannot be deleted right away, as there might be jobs persisted to disk that have it.
 * Removing it can cause deserialization errors which may corrupt the job data.
 */
@Deprecated
public class GitCommitAction extends DatadogPluginAction {

    @Serial
    private static final long serialVersionUID = 1L;

    private volatile String tag;
    private volatile String commit;
    private volatile String message;
    private volatile String authorName;
    private volatile String authorEmail;
    private volatile String authorDate;
    private volatile String committerName;
    private volatile String committerEmail;
    private volatile String committerDate;

    public GitCommitAction() {
    }

    GitCommitAction(String tag, String commit, String message, String authorName, String authorEmail, String authorDate, String committerName, String committerEmail, String committerDate) {
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

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getCommit() {
        return commit;
    }

    public void setCommit(String commit) {
        this.commit = commit;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public String getAuthorDate() {
        return authorDate;
    }

    public void setAuthorDate(String authorDate) {
        this.authorDate = authorDate;
    }

    public String getCommitterName() {
        return committerName;
    }

    public void setCommitterName(String committerName) {
        this.committerName = committerName;
    }

    public String getCommitterEmail() {
        return committerEmail;
    }

    public void setCommitterEmail(String committerEmail) {
        this.committerEmail = committerEmail;
    }

    public String getCommitterDate() {
        return committerDate;
    }

    public void setCommitterDate(String committerDate) {
        this.committerDate = committerDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitCommitAction that = (GitCommitAction) o;
        return Objects.equals(tag, that.tag) && Objects.equals(commit, that.commit) && Objects.equals(message, that.message) && Objects.equals(authorName, that.authorName) && Objects.equals(authorEmail, that.authorEmail) && Objects.equals(authorDate, that.authorDate) && Objects.equals(committerName, that.committerName) && Objects.equals(committerEmail, that.committerEmail) && Objects.equals(committerDate, that.committerDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, commit, message, authorName, authorEmail, authorDate, committerName, committerEmail, committerDate);
    }

    @Override
    public String toString() {
        return "GitCommitAction{" +
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

    public static final class ConverterImpl extends DatadogConverter<GitCommitAction> {
        public ConverterImpl(XStream xs) {
            super(ignoreOldData(), new ConverterV1());
        }
    }

    public static final class ConverterV1 extends VersionedConverter<GitCommitAction> {

        private static final int VERSION = 1;

        public ConverterV1() {
            super(VERSION);
        }

        @Override
        public void marshal(GitCommitAction action, HierarchicalStreamWriter writer, MarshallingContext context) {
            if (action.tag != null) {
                writeField("tag", action.tag, writer, context);
            }
            if (action.commit != null) {
                writeField("commit", action.commit, writer, context);
            }
            if (action.message != null) {
                writeField("message", action.message, writer, context);
            }
            if (action.authorName != null) {
                writeField("authorName", action.authorName, writer, context);
            }
            if (action.authorEmail != null) {
                writeField("authorEmail", action.authorEmail, writer, context);
            }
            if (action.authorDate != null) {
                writeField("authorDate", action.authorDate, writer, context);
            }
            if (action.committerName != null) {
                writeField("committerName", action.committerName, writer, context);
            }
            if (action.committerEmail != null) {
                writeField("committerEmail", action.committerEmail, writer, context);
            }
            if (action.committerDate != null) {
                writeField("committerDate", action.committerDate, writer, context);
            }
        }

        @Override
        public GitCommitAction unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            String tag = null;
            String commit = null;
            String message = null;
            String authorName = null;
            String authorEmail = null;
            String authorDate = null;
            String committerName = null;
            String committerEmail = null;
            String committerDate = null;

            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String fieldName = reader.getNodeName();
                switch (fieldName) {
                    case "tag":
                        tag = (String) context.convertAnother(null, String.class);
                        break;
                    case "commit":
                        commit = (String) context.convertAnother(null, String.class);
                        break;
                    case "message":
                        message = (String) context.convertAnother(null, String.class);
                        break;
                    case "authorName":
                        authorName = (String) context.convertAnother(null, String.class);
                        break;
                    case "authorEmail":
                        authorEmail = (String) context.convertAnother(null, String.class);
                        break;
                    case "authorDate":
                        authorDate = (String) context.convertAnother(null, String.class);
                        break;
                    case "committerName":
                        committerName = (String) context.convertAnother(null, String.class);
                        break;
                    case "committerEmail":
                        committerEmail = (String) context.convertAnother(null, String.class);
                        break;
                    case "committerDate":
                        committerDate = (String) context.convertAnother(null, String.class);
                        break;
                    default:
                        // unknown tag, could be something serialized by a different version of the plugin
                        break;
                }
                reader.moveUp();
            }

            return new GitCommitAction(tag, commit, message, authorName, authorEmail, authorDate, committerName, committerEmail, committerDate);
        }
    }
}
