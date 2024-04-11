package org.datadog.jenkins.plugins.datadog.configuration.api.key;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.Collections;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Symbol("datadogCredentialsApiKey")
public class DatadogCredentialsApiKey extends DatadogApiKey {

    private final String credentialsId;

    @DataBoundConstructor
    public DatadogCredentialsApiKey(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public Secret getKey() {
        StringCredentials credential = getCredentialFromId(credentialsId);
        return credential != null ? credential.getSecret() : null;
    }

    /**
     * Gets the StringCredentials object for the given credential ID
     *
     * @param credentialId - The ID of the credential to get
     * @return a StringCredentials object
     */
    static StringCredentials getCredentialFromId(String credentialId) {
        if (StringUtils.isBlank(credentialId)) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StringCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        URIRequirementBuilder.fromUri(null).build()),
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialId))
        );
    }

    @Override
    public Descriptor<DatadogApiKey> getDescriptor() {
        return Jenkins.getInstanceOrNull().getDescriptorOrDie(DatadogCredentialsApiKey.class);
    }

    @Extension
    public static final class DatadogCredentialsApiKeyDescriptor extends DatadogApiKeyDescriptor {
        public DatadogCredentialsApiKeyDescriptor() {
            load();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Select from credentials";
        }

        @Override
        public String getHelpFile() {
            return getHelpFile("credentialsKeyBlock");
        }

        /**
         * Populates the API key credentials ID dropdown in the configuration screen with all the valid credentials
         *
         * @param item - The context within which to list available credentials
         * @param credentialsId - ID of the credential containing the API key
         * @return a ListBoxModel object used to display all the available credentials.
         */
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter("credentialsId") String credentialsId
        ) {
            StandardListBoxModel result = new StandardListBoxModel();
            // If the user does not have permissions to list credentials, only list the current value
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM,
                            Jenkins.get(),
                            StringCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(StringCredentials.class))
                    .includeCurrentValue(credentialsId);
        }

        @RequirePOST
        public FormValidation doCheckCredentialsId(
                @AncestorInPath Item item,
                @QueryParameter("credentialsId") String credentialsId
        ) {
            // Don't validate for users that do not have permission to list credentials
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.ok();
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
            }
            if (StringUtils.isBlank(credentialsId)) {
                return FormValidation.ok();
            }
            if (credentialsId.startsWith("${") && credentialsId.endsWith("}")) {
                return FormValidation.warning("Cannot validate expression based credentials");
            }
            if (CredentialsProvider.listCredentials(StringCredentials.class,
                    item,
                    ACL.SYSTEM,
                    Collections.emptyList(),
                    CredentialsMatchers.withId(credentialsId)).isEmpty()) {
                return FormValidation.error("Cannot find currently selected credentials");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doCheckConnectivity(@QueryParameter("credentialsId") final String credentialsId,
                                                  @QueryParameter("apiUrl") final String apiUrl) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            StringCredentials credentialFromId = getCredentialFromId(credentialsId);
            if (credentialFromId == null) {
                return FormValidation.error("Could not find credentials with the given ID");
            }
            Secret apiKeyValue = credentialFromId.getSecret();
            if (validateApiConnection(apiUrl, apiKeyValue)) {
                return FormValidation.ok("Great! Your API key is valid.");
            } else {
                return FormValidation.error("Hmmm, your API key seems to be invalid.");
            }
        }
    }
}
