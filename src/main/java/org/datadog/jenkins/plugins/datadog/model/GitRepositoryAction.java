package org.datadog.jenkins.plugins.datadog.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Objects;
import javax.annotation.Nullable;
import org.datadog.jenkins.plugins.datadog.util.DatadogActionConverter;

/**
 * Keeps the Git repository related information.
 */
public class GitRepositoryAction extends DatadogPluginAction {

    private static final long serialVersionUID = 1L;

    private volatile String repositoryURL;
    private volatile String defaultBranch;
    private volatile String branch;

    public GitRepositoryAction() {
    }

    public GitRepositoryAction(String repositoryURL, String defaultBranch, String branch) {
        this.repositoryURL = repositoryURL;
        this.defaultBranch = defaultBranch;
        this.branch = branch;
    }

    @Nullable
    public String getRepositoryURL() {
        return repositoryURL;
    }

    public void setRepositoryURL(String repositoryURL) {
        this.repositoryURL = repositoryURL;
    }

    @Nullable
    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    @Nullable
    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitRepositoryAction that = (GitRepositoryAction) o;
        return Objects.equals(repositoryURL, that.repositoryURL) && Objects.equals(defaultBranch, that.defaultBranch) && Objects.equals(branch, that.branch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryURL, defaultBranch, branch);
    }

    @Override
    public String toString() {
        return "GitRepositoryAction{" +
                "repositoryURL='" + repositoryURL + '\'' +
                ", defaultBranch='" + defaultBranch + '\'' +
                ", branch='" + branch + '\'' +
                '}';
    }

    public static final class ConverterImpl extends DatadogActionConverter {
        public ConverterImpl(XStream xs) {
        }

        @Override
        public boolean canConvert(Class type) {
            return GitRepositoryAction.class == type;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            GitRepositoryAction action = (GitRepositoryAction) source;
            if (action.repositoryURL != null) {
                writeField("repositoryURL", action.repositoryURL, writer, context);
            }
            if (action.defaultBranch != null) {
                writeField("defaultBranch", action.defaultBranch, writer, context);
            }
            if (action.branch != null) {
                writeField("branch", action.branch, writer, context);
            }
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            GitRepositoryAction gitRepositoryAction = new GitRepositoryAction();
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String fieldName = reader.getNodeName();
                switch (fieldName) {
                    case "repositoryURL":
                        gitRepositoryAction.setRepositoryURL((String) context.convertAnother(null, String.class));
                        break;
                    case "defaultBranch":
                        gitRepositoryAction.setDefaultBranch((String) context.convertAnother(null, String.class));
                        break;
                    case "branch":
                        gitRepositoryAction.setBranch((String) context.convertAnother(null, String.class));
                        break;
                }
                reader.moveUp();
            }
            return gitRepositoryAction;
        }
    }
}
