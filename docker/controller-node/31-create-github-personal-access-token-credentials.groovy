import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import jenkins.model.Jenkins

def privateKey = System.getenv("GITHUB_SSH_KEY")
if (privateKey != null && !privateKey.isEmpty()) {
    def credentialId = 'github-personal-access-token'
    def username = System.getenv("GITHUB_USERNAME")
    def password = System.getenv("GITHUB_PERSONAL_ACCESS_TOKEN")
    def description = 'Personal Access Token for GitHub Branch Source plugin'

    def credentials = new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL,
            credentialId,
            description,
            username,
            password,
    )
    CredentialsProvider.lookupStores(Jenkins.instance).first().addCredentials(Domain.global(), credentials)
}
