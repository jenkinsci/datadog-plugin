# Jenkins Datadog Plugin

[![Build Status](https://dev.azure.com/datadoghq/jenkins-datadog-plugin/_apis/build/status/DataDog.jenkins-datadog-plugin?branchName=master)](https://dev.azure.com/datadoghq/jenkins-datadog-plugin/_build/latest?definitionId=18&branchName=master)

A Jenkins plugin used to forward automatically metrics, events, and service checks to a Datadog account.

**Note**: There is a [Jenkins CI Plugin page][1] for this plugin, but it refers to this documentation.

## Setup

### Installation

_This plugin requires [Jenkins 1.580.1][2] or newer._

This plugin can be installed from the [Update Center][3] (found at `Manage Jenkins -> Manage Plugins`) in your Jenkins installation:

1. Select the `Available` tab, search for `Datadog` and look for `Datadog Plugin`.
2. Check the checkbox next to it, and install via your preference by using one of the two install buttons at the bottom of the screen.
3. Check that the plugin has been successfully installed by searching for `Datadog Plugin` on the `Installed` tab. If the plugin has been successfully installed, continue on to the configuration step, described below.

**Note**: If you do not see the version of `Datadog Plugin` that you are expecting, make sure you have run `Check Now` from the `Manage Jenkins -> Manage Plugins` screen.

### Configuration

You can use two ways to configure your plugin to submit data to Datadog:

* Sending the data directly to Datadog through HTTP.
* Using a DogStatsD server that acts as a forwarder between Jenkins and Datadog.

This configuration can be done from the [plugin user interface](#plugin-user-interface), thanks to a [Groovy script](#groovy-script), or through [Environment variables](#environment-variables).

#### Plugin user interface

To configure your Datadog Plugin, navigate to the `Manage Jenkins -> Configure System` page on your Jenkins installation. Once there, scroll down to find the `Datadog Plugin` section:

##### HTTP forwarding {#http-forwarding-plugin}

1. Click the "Use Datadog API URL and Key to report to Datadog" radio button (selected by default)
2. Find your API Key from the [API Keys][4] page on your Datadog account, and copy/paste it into the `API Key` textbox on the Jenkins configuration screen.
3. You can test that your API Key works by pressing the `Test Key` button, on the Jenkins configuration screen, directly below the API Key textbox.
4. Save your configuration.

##### DogStatsD forwarding {#dogstatsd-forwarding-plugin}

1. Click the **Use a DogStatsD Server to report to Datadog** radio button.
2. Specify both your DogStatD server `hostname` and `port`
3. Save your configuration.

#### Groovy script

Configure your Datadog plugin to forward its data through HTTP or DogStatsD using the Groovy scripts below. Configuring the plugin this way might be useful if you're running your Jenkins Master in a Docker container using the [Official Jenkins Docker Image][5] or any derivative that supports plugins.txt and Groovy init scripts.

##### HTTP forwarding {#http-forwarding-groovy-script}

```groovy
import jenkins.model.*
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration

def j = Jenkins.getInstance()
def d = j.getDescriptor("org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration")

// If you want to use Datadog API URL and Key to report to Datadog
d.setReportWith('HTTP')
d.setTargetApiURL('https://api.datadoghq.com/api/')
d.setTargetApiKey('<DATADOG_API_KEY>')

// Customization, see dedicated section below
d.setBlacklist('job1,job2')

// Save config
d.save()
```

##### DogStatsD forwarding {#dogstatsd-forwarding-groovy-script}

```groovy
import jenkins.model.*
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration

def j = Jenkins.getInstance()
def d = j.getDescriptor("org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration")

d.setReportWith('DSD')
d.setTargetHost('localhost')
d.setTargetPort(8125)

// Customization, see dedicated section below
d.setBlacklist('job1,job2')

// Save config
d.save()
```

#### Environment variables

Configure your Datadog plugin using environment variables by specifying the `DATADOG_JENKINS_PLUGIN_REPORT_WITH` variable which specifies which report mechanism you want to use.

##### HTTP Forwarding {#http-forwarding-env}

1. Set the `DATADOG_JENKINS_PLUGIN_REPORT_WITH` variable to `HTTP`.
2. Set the `DATADOG_JENKINS_PLUGIN_TARGET_API_URL` parameter which specifies the Datadog API Endpoint to report to. Default value is `https://api.datadoghq.com/api/`.
3. Set the `DATADOG_JENKINS_PLUGIN_TARGET_API_KEY` parameter which specifies your Datadog API key in order to report to your Datadog account. Get your API Key from the [Datadog API Keys page][4].

##### DogStatsD forwarding {#dogstatsd-forwarding-env}

1. Set the `DATADOG_JENKINS_PLUGIN_REPORT_WITH` variable to `DSD`.
2. Set the `DATADOG_JENKINS_PLUGIN_TARGET_HOST` variable which specifies the DogStatsD Server host to report to. Default value is `localhost`.
3. Set the `DATADOG_JENKINS_PLUGIN_TARGET_PORT` variable which specifies the DogStatsD Server port to report to. Default value is `8125`.

#### Logging

Logging is done by utilizing the `java.util.Logger`, which follows the [best logging practices for Jenkins][6]. In order to obtain logs, follow the directions listed in [the Jenkins logging documentation][6]. When adding a Logger, all Datadog plugin functions start with `org.datadog.jenkins.plugins.datadog.` and the function name you are after should autopopulate. As of this writing, the only function available was `org.datadog.jenkins.plugins.datadog.listeners.DatadogBuildListener`.

## Customization

### Global Customization

From the global configuration page, at `Manage Jenkins -> Configure System` you can customize your configuration with:

| Customization              | Description                                                                                                                                                                                                                                 | Environment Variable                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| Blacklisted Jobs           | A comma-separated list of regex to match job names that should not be monitored, e.g.: `susans-job,johns-.*,prod_folder/prod_release`.                                                                                                      | `DATADOG_JENKINS_PLUGIN_BLACKLIST`            |
| Whitelisted Jobs           | A comma-separated list of regex to match job names that should be monitored, e.g.: `susans-job,johns-.*,prod_folder/prod_release`.                                                                                                          | `DATADOG_JENKINS_PLUGIN_WHITELIST`            |
| Global Tag File            | Path to the workspace file containing a comma separated list of tags (not compatible with Pipeline jobs).                                                                                                                                   | `DATADOG_JENKINS_PLUGIN_GLOBAL_TAG_FILE`      |
| Global Tags                | A comma-separated list of tags to apply to all metrics, events, and service checks.                                                                                                                                                         | `DATADOG_JENKINS_PLUGIN_GLOBAL_TAGS`          |
| Global Job Tags            | A regex to match a job, and a list of tags to apply to that job, all separated by a comma. Note: tags can reference match groups in the regex using the `$` symbol, e.g.: `(.*?)_job_(*?)_release, owner:$1, release_env:$2, optional:Tag3` | `DATADOG_JENKINS_PLUGIN_GLOBAL_JOB_TAGS`      |
| Send Security audit events | Enabled by default, it submits `Security Events Type` of events and metrics.                                                                                                                                                                | `DATADOG_JENKINS_PLUGIN_EMIT_SECURITY_EVENTS` |
| Send System events         | Enabled by default, it submits `System Events Type` of events and metrics                                                                                                                                                                   | `DATADOG_JENKINS_PLUGIN_EMIT_SYSTEM_EVENTS`   |

### Job Customization

From a job specific configuration page:

| Customization                         | Description                                                                                                                                                                                           |
|---------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Custom tags                           | Set it from a `File` in the job workspace (not compatible with Pipeline jobs) or as text `Properties` directly from the configuration page. If set, it overrides the `Global Job Tags` configuration. |
| Send Source Control Management events | Enabled by default, it submits `Source Control Management Events Type` of events and metrics.                                                                                                         |

## Data Collected

The plugin is collecting the following [Events](#events), [Metrics](#metrics), and [Service Checks](#service-checks):

### Events

#### Default Events Type

| Event Name      | Triggered on              | Default Tags                                                               | Associated RATE metric  |
|-----------------|---------------------------|----------------------------------------------------------------------------|-------------------------|
| Build Started   | `RunListener#onStarted`   | `job`, `node`, `branch`                                                    | `jenkins.job.started`   |
| Build Aborted   | `RunListener#onDeleted`   | `job`, `node`, `branch`                                                    | `jenkins.job.aborted`   |
| Build Completed | `RunListener#onCompleted` | `job`, `node`, `branch`, `result` (Git Branch, SVN revision or CVS branch) | `jenkins.job.completed` |

#### Source Control Management Events Type

| Event Name   | Triggered on             | Default Tags            | Associated RATE metric |
|--------------|--------------------------|-------------------------|------------------------|
| SCM Checkout | `SCMListener#onCheckout` | `job`, `node`, `branch` | `jenkins.scm.checkout` |

#### Systems Events Type

| Event Name                   | Triggered on                            | Associated RATE metric                 |
|------------------------------|-----------------------------------------|----------------------------------------|
| Computer Online              | `ComputerListener#onOnline`             | `jenkins.computer.online`              |
| Computer Offline             | `ComputerListener#onOffline`            | `jenkins.computer.online`              |
| Computer TemporarilyOnline   | `ComputerListener#onTemporarilyOnline`  | `jenkins.computer.temporarily_online`  |
| Computer TemporarilyOffline  | `ComputerListener#onTemporarilyOffline` | `jenkins.computer.temporarily_offline` |
| Computer LaunchFailure       | `ComputerListener#onLaunchFailure`      | `jenkins.computer.launch_failure`      |
| Item Created                 | `ItemListener#onCreated`                | `jenkins.item.created`                 |
| Item Deleted                 | `ItemListener#onDeleted`                | `jenkins.item.deleted`                 |
| Item Updated                 | `ItemListener#onUpdated`                | `jenkins.item.updated`                 |
| Item Copied                  | `ItemListener#onCopied`                 | `jenkins.item.copied`                  |
| ItemListener LocationChanged | `ItemListener#onLocationChanged`        | `jenkins.item.location_changed`        |
| Config Changed               | `SaveableListener#onChange`             | `jenkins.config.changed`               |

#### Security Events Type

| Event Name                  | Triggered on                            | Associated RATE metric       |
|-----------------------------|-----------------------------------------|------------------------------|
| User Authenticated          | `SecurityListener#authenticated`        | `jenkins.user.authenticated` |
| User failed To Authenticate | `SecurityListener#failedToAuthenticate` | `jenkins.user.access_denied` |
| User loggedOut              | `SecurityListener#loggedOut`            | `jenkins.user.logout`        |

### Metrics

| Metric Name                            | Description                                                    | Default Tags                               |
|----------------------------------------|----------------------------------------------------------------|--------------------------------------------|
| `jenkins.computer.launch_failure`      | Rate of computer launch failures.                              |                                            |
| `jenkins.computer.offline`             | Rate of computer going offline.                                |                                            |
| `jenkins.computer.online`              | Rate of computer going online.                                 |                                            |
| `jenkins.computer.temporarily_offline` | Rate of computer going temporarily offline.                    |                                            |
| `jenkins.computer.temporarily_online`  | Rate of computer going temporarily online.                     |                                            |
| `jenkins.config.changed`               | Rate of configs being changed.                                 |                                            |
| `jenkins.executor.count`               | Executor count.                                                | `node_hostname`, `node_name`, `node_label` |
| `jenkins.executor.free`                | Number of unused executor.                                     | `node_hostname`, `node_name`, `node_label` |
| `jenkins.executor.in_use`              | Number of idle executor.                                       | `node_hostname`, `node_name`, `node_label` |
| `jenkins.item.copied`                  | Rate of items being copied.                                    |                                            |
| `jenkins.item.created`                 | Rate of items being created.                                   |                                            |
| `jenkins.item.deleted`                 | Rate of items being deleted.                                   |                                            |
| `jenkins.item.location_changed`        | Rate of items being moved.                                     |                                            |
| `jenkins.item.updated`                 | Rate of items being updated.                                   |                                            |
| `jenkins.job.aborted`                  | Rate of aborted jobs.                                          | `branch`, `job`, `node`                    |
| `jenkins.job.completed`                | Rate of completed jobs.                                        | `branch`, `job`, `node`, `result`          |
| `jenkins.job.cycletime`                | Build Cycle Time.                                              | `branch`, `job`, `node`, `result`          |
| `jenkins.job.duration`                 | Build duration (in seconds).                                   | `branch`, `job`, `node`, `result`          |
| `jenkins.job.feedbacktime`             | Feedback time from code commit to job failure.                 | `branch`, `job`, `node`, `result`          |
| `jenkins.job.leadtime`                 | Build Lead Time.                                               | `branch`, `job`, `node`, `result`          |
| `jenkins.job.mtbf`                     | MTBF, time between last successful job and current failed job. | `branch`, `job`, `node`, `result`          |
| `jenkins.job.mttr`                     | MTTR: time between last failed job and current successful job. | `branch`, `job`, `node`, `result`          |
| `jenkins.job.started`                  | Rate of started jobs.                                          | `branch`, `job`, `node`                    |
| `jenkins.job.waiting`                  | Time spent waiting for job to run (in milliseconds).           | `branch`, `job`, `node`                    |
| `jenkins.node.count`                   | Total number of node.                                          |                                            |
| `jenkins.node.offline`                 | Offline nodes count.                                           |                                            |
| `jenkins.node.online`                  | Online nodes count.                                            |                                            |
| `jenkins.plugin.count`                 | Plugins count.                                                 |                                            |
| `jenkins.project.count`                | Project count.                                                 |                                            |
| `jenkins.queue.size`                   | Queue Size.                                                    |                                            |
| `jenkins.queue.buildable`              | Number of Buildable item in Queue.                             |                                            |
| `jenkins.queue.pending`                | Number of Pending item in Queue.                               |                                            |
| `jenkins.queue.stuck`                  | Number of Stuck item in Queue.                                 |                                            |
| `jenkins.queue.blocked`                | Number of Blocked item in Queue.                               |                                            |
| `jenkins.scm.checkout`                 | Rate of SCM checkouts.                                         | `branch`, `job`, `node`                    |
| `jenkins.user.access_denied`           | Rate of users failing to authenticate.                         |                                            |
| `jenkins.user.authenticated`           | Rate of users authenticating.                                  |                                            |
| `jenkins.user.logout`                  | Rate of users logging out.                                     |                                            |

### Service checks

Build status `jenkins.job.status` with the default tags: : `job`, `node`, `branch`, `result` (Git Branch, SVN revision or CVS branch)

**Note**: Git `branch` tag is available when using the [Git Plugin][7].

## Release Process

### Overview

The [DataDog/jenkins-datadog-plugin][8] repository handles the most up-to-date changes made to the Datadog Plugin, as well as issue tickets revolving around that work. Releases are merged to the [Jenkins-CI git repo for our plugin][9], and represents the source used for plugin releases found in the [Update Center][3] in your Jenkins installation.

Every commit to the [DataDog/jenkins-datadog-plugin][8] repository triggers a Jenkins build on our internal Jenkins installation.

A list of releases is available at [jenkinsci/datadog-plugin/releases][10].

### How to Release

To release a new plugin version, change the project version in the [pom.xml][11] from `x.x.x-SNAPSHOT` to the updated version number you would like to see. Add an entry for the new release number to the [CHANGELOG.md][12] file, and ensure that all the changes are listed accurately. Then run the `jenkins-datadog-plugin-release` job in the Jenkins installation. If the job completes successfully, then the newly updated plugin should be available from the Jenkins [Update Center][3] within ~4 hours (plus mirror propogation time).

## Issue Tracking

The Github's built in issue tracking system is used to track all issues tickets relating to this plugin, found [DataDog/jenkins-datadog-plugin/issues][13]. However, given how Jenkins Plugins are hosted, there may be issues that are posted to JIRA as well. You can check [this jenkins issue][14] for those issue postings.

**Note**: [Unresolved issues on JIRA mentioning Datadog.][15].

## Changes

See the [CHANGELOG.md][12].

## How to contribute code

First of all and most importantly, **thank you** for sharing.

If you want to submit code, fork this repository and submit pull requests against the `master` branch. For more information, checkout the [contributing guidelines][16] for the Datadog Agent.

Check out the [development document][17] for tips on spinning up a quick development environment locally.

## Manual Testing

In order to keep track of some testing procedures for ensuring proper functionality of the Datadog Plugin on Jenkins, there is a [testing document][17].

[1]: https://plugins.jenkins.io/datadog
[2]: http://updates.jenkins-ci.org/download/war/1.580.1/jenkins.war
[3]: https://wiki.jenkins-ci.org/display/JENKINS/Plugins#Plugins-Howtoinstallplugins
[4]: https://app.datadoghq.com/account/settings#api
[5]: https://github.com/jenkinsci/docker
[6]: https://wiki.jenkins-ci.org/display/JENKINS/Logging
[7]: https://wiki.jenkins.io/display/JENKINS/Git+Plugin
[8]: https://github.com/DataDog/jenkins-datadog-plugin
[9]: https://github.com/jenkinsci/datadog-plugin
[10]: https://github.com/jenkinsci/datadog-plugin/releases
[11]: pom.xml
[12]: CHANGELOG.md
[13]: https://github.com/DataDog/jenkins-datadog-plugin/issues
[14]: https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20%28Open%2C%20%22In%20Progress%22%2C%20Reopened%29%20AND%20component%20%3D%20datadog-plugin%20ORDER%20BY%20updated%20DESC%2C%20priority%20DESC%2C%20created%20ASC
[15]: https://issues.jenkins-ci.org/browse/INFRA-305?jql=status%20in%20%28Open%2C%20%22In%20Progress%22%2C%20Reopened%2C%20Verified%2C%20Untriaged%2C%20%22Fix%20Prepared%22%29%20AND%20text%20~%20%22datadog%22
[16]: https://github.com/DataDog/datadog-agent/blob/master/CONTRIBUTING.md
[17]: CONTRIBUTING.md
