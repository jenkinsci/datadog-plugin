// A script that configures sample Jenkins shared library (https://www.jenkins.io/doc/book/pipeline/shared-libraries/)

import jenkins.plugins.git.GitSCMSource
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever

// Define the shared library configuration
def libraryName = "test-shared-lib"
def defaultVersion = "main"
def gitUrl = "git@github.com:DataDog/ci-visibility-test-jenkins-shared-library.git"
def credentialsId = "github-ssh"

// Create a Git SCM source
def gitSCMSource = new GitSCMSource(null, gitUrl, credentialsId, "*", "", false)

// Create a retriever using the SCM source
def retriever = new SCMSourceRetriever(gitSCMSource)

// Create the library configuration
def libraryConfig = new LibraryConfiguration(libraryName, retriever)
libraryConfig.defaultVersion = defaultVersion
libraryConfig.implicit = false
libraryConfig.allowVersionOverride = true

// Add the library configuration to the global libraries
def globalLibraries = GlobalLibraries.get()
def existingLibraries = globalLibraries.libraries.findAll { it.name != libraryName }
globalLibraries.libraries = existingLibraries + libraryConfig

println "Global Trusted Pipeline Library '${libraryName}' has been configured."
