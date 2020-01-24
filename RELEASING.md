# Releasing

This document summarizes the process of doing a new release of this project.
Release can only be performed by Datadog maintainers of this repository.

## Schedule
This project does not have a strict release schedule. However, we would make a release at least every 2 months.
  - No release will be done if no changes got merged to the `master` branch during the above mentioned window.
  - Releases may be done more frequently than the above mentioned window.

## Prerelease checklist

* Check and upgrade dependencies where it applies and makes sense.
  - Create a distinct pull request and test your changes since it may introduce regressions.
  - While using the latest versions of dependencies is advised, it may not always be possible due to potential compatibility issues.
  - Upgraded dependencies should be thoroughly considered and tested to ensure they are safe!
* Make sure tests are passing.
  - Locally and in the continuous integration system.
* Manually test changes included in the new release.
* Make sure documentation is up-to-date.
* [Update changelog](#update-changelog)
  - Create a distinct pull request.

## Update Changelog

### Prerequisite

- Install [datadog_checks_dev](https://datadog-checks-base.readthedocs.io/en/latest/datadog_checks_dev.cli.html#installation) using Python 3

### Commands

- See changes ready for release by running `ddev release show changes .` at the root of this project. Add any missing labels to PRs if needed.
- Run `ddev release changelog . <NEW_VERSION>` to update the `CHANGELOG.md` file at the root of this repository.
- Commit the changes to the repository in a release branch and get it approved/merged.

## Release Process

Our team will trigger the release pipeline which will update the [GitHub JenkinsCI Datadog Plugin Repository][1].
It will create new a [GitHub tag and release][2] and push artifacts to the [Jenkins CI Org Repo][3] 
See the [Jenkins Publishing Documentation][4] for more details about the process.

Once releases, the new version should be available in the [Update Center][5].
Releases are merged to the [Jenkins-CI git repository for the Datadog-plugin][1], and represents the source used for plugin releases found in the [Update Center][5] in your Jenkins installation.

### How to release

To release a new version:

1. Change the project version in the [pom.xml][6] from `x.x.x-SNAPSHOT` to the updated version number you would like to see. 
2. Add an entry for the new release number to the [CHANGELOG.md][7] file, and ensure that all changes are listed accurately. 
3. Clone the repository and checkout the `master` branch with all above changes merged in.
4. We will trigger our release pipeline. If completed successfully, the newly updated plugin should be available from the Jenkins [Update Center][5] within ~4 hours (plus mirror propagation time).

[1]: https://github.com/jenkinsci/datadog-plugin
[2]: https://github.com/jenkinsci/datadog-plugin/releases
[3]: https://repo.jenkins-ci.org/releases/org/datadog/jenkins/plugins/datadog/
[4]: https://jenkins.io/doc/developer/publishing/artifact-repository/
[5]: https://wiki.jenkins-ci.org/display/JENKINS/Plugins#Plugins-Howtoinstallplugins
[6]: https://github.com/jenkinsci/datadog-plugin/blob/master/pom.xml
[7]: https://github.com/jenkinsci/datadog-plugin/blob/master/CHANGELOG.md
