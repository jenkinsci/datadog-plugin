import jenkins.branch.OrganizationFolder
import jenkins.model.Jenkins
import jenkins.scm.impl.SingleSCMNavigator
import org.jenkinsci.plugins.github_branch_source.*

GitHubSCMSource source = new GitHubSCMSource("DataDog", "ci-visibility-test-jenkins", "https://github.com/DataDog/ci-visibility-test-jenkins.git", true)
source.credentialsId = "github-personal-access-token"
source.traits = [
        new BranchDiscoveryTrait(1),
        new OriginPullRequestDiscoveryTrait(2)
]

SingleSCMNavigator scmNavigator = new SingleSCMNavigator("ci-visibility-test-jenkins", [ source ])

OrganizationFolder orgFolder = Jenkins.instance.createProject(OrganizationFolder.class, "test-organization-folder")
orgFolder.navigators.replace(scmNavigator)

orgFolder.scheduleBuild()
orgFolder.save()
