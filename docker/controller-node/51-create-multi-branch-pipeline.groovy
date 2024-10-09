import jenkins.branch.BranchSource
import jenkins.model.Jenkins
import jenkins.plugins.git.traits.BranchDiscoveryTrait
import jenkins.plugins.git.GitSCMSource
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject

def jobName = "test-multibranch-pipeline"
def repoUrl = "git@github.com:DataDog/ci-visibility-test-jenkins.git"
def credentialsId = "github-ssh"

def jenkinsInstance = Jenkins.instance

def existingJob = jenkinsInstance.getItem(jobName)
if (existingJob) {
    println "Job with name ${jobName} already exists."
    return
}

def mbp = jenkinsInstance.createProject(WorkflowMultiBranchProject, jobName)

def gitSCMSource = new GitSCMSource(null, repoUrl, credentialsId, "*", "", false)
gitSCMSource.traits = [new BranchDiscoveryTrait()]
def branchSource = new BranchSource(gitSCMSource)
mbp.sourcesList.add(branchSource)

mbp.save()

println "Multibranch pipeline ${jobName} created with repository: ${repoUrl}"
