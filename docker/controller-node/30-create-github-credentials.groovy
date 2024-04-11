import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import jenkins.model.Jenkins

def privateKey = System.getenv("GITHUB_SSH_KEY")
if (privateKey != null && !privateKey.isEmpty()) {
    def credentialId = 'github-ssh'
    def username = 'github'
    def privateKeyPassphrase = System.getenv("GITHUB_SSH_KEY_PASSPHRASE")
    def description = 'SSH Credentials for Github'

    def credentials = new BasicSSHUserPrivateKey(
            CredentialsScope.GLOBAL,
            credentialId,
            username,
            new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey),
            privateKeyPassphrase,
            description
    )
    CredentialsProvider.lookupStores(Jenkins.instance).first().addCredentials(Domain.global(), credentials)
}
