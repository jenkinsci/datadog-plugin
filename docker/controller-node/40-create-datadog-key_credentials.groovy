import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import hudson.util.Secret
import jenkins.model.Jenkins
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl

def privateKey = System.getenv("GITHUB_SSH_KEY")
if (privateKey != null && !privateKey.isEmpty()) {
    def credentialId = 'datadog-api-key'
    def description = 'Datadog API key'
    def datadogKey = System.getenv('JENKINS_PLUGIN_DATADOG_API_KEY')
    if (datadogKey) {
        def credentials = new StringCredentialsImpl(
                CredentialsScope.GLOBAL,
                credentialId,
                description,
                Secret.fromString(datadogKey)
        )

        CredentialsProvider.lookupStores(Jenkins.instance).first().addCredentials(Domain.global(), credentials)
    } else {
        throw new IllegalArgumentException("JENKINS_PLUGIN_DATADOG_API_KEY environment variable is not set")
    }
}
