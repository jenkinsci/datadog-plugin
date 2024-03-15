import hudson.security.HudsonPrivateSecurityRealm
import jenkins.model.Jenkins

def instance = Jenkins.getInstance()

def hudsonRealm = new HudsonPrivateSecurityRealm(false)
instance.setSecurityRealm(hudsonRealm)

def user = hudsonRealm.createAccount("admin", "local-jenkins-instance-admin-password")
user.setFullName("Administrator")
user.save()

instance.save()
