# Jenkins Datadog Plugin

A Jenkins plugin for automatically forwarding metrics, events, and service checks to a Datadog account.

**Note**: The [Jenkins CI plugin page][1] for this plugin references this documentation.

## Setup

### Installation

_This plugin requires [Jenkins 2.164.1][2] or newer._

This plugin can be installed from the [Update Center][3] (found at `Manage Jenkins -> Manage Plugins`) in your Jenkins installation:

1. Select the `Available` tab, search for `Datadog`, and select the checkbox next to `Datadog Plugin`.
2. Install the plugin by using one of the two install buttons at the bottom of the screen.
3. To verify the plugin is installed, search for `Datadog Plugin` on the `Installed` tab. 
4. Create a [custom log source file][13]: create a `conf.yaml` inside `conf.d/jenkins.d` with the following:
  ```
  logs:

    -type: tcp 
     port: 10518 
     service: <SERVICE>
     source: jenkins
  ```
  
  Continue below for configuration.

**Note**: If you see an unexpected version of the `Datadog Plugin`, run `Check Now` from the `Manage Jenkins -> Manage Plugins` screen.

### Configuration

You can use two ways to configure your plugin to submit data to Datadog:

* **RECOMMENDED**: Using a DogStatsD server / Datadog Agent that acts as a forwarder between Jenkins and Datadog.
  - Build Logs collection only works with a full Datadog Agent installed.
* Sending data directly to Datadog through HTTP.
  - The HTTP client implementation used is blocking with a timeout duration of 1 minute. If there is a connection problem with Datadog, it may slow your Jenkins instance down.

The configuration can be done from the [plugin user interface](#plugin-user-interface) with a [Groovy script](#groovy-script), or through [environment variables](#environment-variables).

#### Plugin user interface

To configure your Datadog Plugin, navigate to the `Manage Jenkins -> Configure System` page on your Jenkins installation. Once there, scroll down to find the `Datadog Plugin` section:

##### HTTP forwarding {#http-forwarding-plugin}

1. Select the radio button next to **Use Datadog API URL and Key to report to Datadog** (selected by default).
2. Use your [Datadog API key][4] in the `API Key` textbox on the Jenkins configuration screen.
3. Test your Datadog API key by using the `Test Key` button on the Jenkins configuration screen directly below the API key textbox.
4. Save your configuration.

##### DogStatsD forwarding {#dogstatsd-forwarding-plugin}

1. Select the radio button next to **Use a DogStatsD Server to report to Datadog**.
2. Specify your DogStatsD server `hostname` and `port`.
3. Save your configuration.

#### Groovy script

Configure your Datadog plugin to forward data through HTTP or DogStatsD using the Groovy scripts below. Configuring the plugin this way might be useful if you're running your Jenkins Master in a Docker container using the [official Jenkins Docker image][5] or any derivative that supports `plugins.txt` and Groovy init scripts.

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

Configure your Datadog plugin using environment variables with the `DATADOG_JENKINS_PLUGIN_REPORT_WITH` variable, which specifies the report mechanism to use.

##### HTTP forwarding {#http-forwarding-env}

1. Set the `DATADOG_JENKINS_PLUGIN_REPORT_WITH` variable to `HTTP`.
2. Set the `DATADOG_JENKINS_PLUGIN_TARGET_API_URL` variable, which specifies the Datadog API endpoint (defaults to `https://api.datadoghq.com/api/`).
3. Set the `DATADOG_JENKINS_PLUGIN_TARGET_API_KEY` variable, which specifies your [Datadog API key][4].
4. (optional) Set the `DATADOG_JENKINS_PLUGIN_TARGET_LOG_INTAKE_URL` variable, which specifies the Datadog Log Intake URL (defaults to `https://http-intake.logs.datadoghq.com/v1/input/`).

##### DogStatsD forwarding {#dogstatsd-forwarding-env}

1. Set the `DATADOG_JENKINS_PLUGIN_REPORT_WITH` variable to `DSD`.
2. Set the `DATADOG_JENKINS_PLUGIN_TARGET_HOST` variable, which specifies the DogStatsD server host (defaults to `localhost`).
3. Set the `DATADOG_JENKINS_PLUGIN_TARGET_PORT` variable, which specifies the DogStatsD server port (defaults to `8125`).
4. (optional) Set the `DATADOG_JENKINS_PLUGIN_TARGET_LOG_COLLECTION_PORT` variable, which specifies the Datadog Agent log collection port.

#### Logging

Logging is done by utilizing the `java.util.Logger`, which follows the [best logging practices for Jenkins][6]. To obtain logs, follow the directions in the [Jenkins logging documentation][6]. When adding a logger, all Datadog plugin functions start with `org.datadog.jenkins.plugins.datadog.` and the function name you are after should autopopulate. As of this writing, the only function available was `org.datadog.jenkins.plugins.datadog.listeners.DatadogBuildListener`.

## Customization

### Global customization

To customize your global configuration, in Jenkins navigate to `Manage Jenkins -> Configure System` then click the **Advanced** button. The following options are available:

| Customization              | Description                                                                                                                                                                                                                                 | Environment variable                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| Blacklisted jobs           | A comma-separated list of regex used to exclude job names from monitoring, for example: `susans-job,johns-.*,prod_folder/prod_release`.                                                                                                      | `DATADOG_JENKINS_PLUGIN_BLACKLIST`            |
| Whitelisted jobs           | A comma-separated list of regex used to include job names for monitoring, for example: `susans-job,johns-.*,prod_folder/prod_release`.                                                                                                          | `DATADOG_JENKINS_PLUGIN_WHITELIST`            |
| Global tag file            | The path to a workspace file containing a comma separated list of tags (not compatible with pipeline jobs).                                                                                                                                   | `DATADOG_JENKINS_PLUGIN_GLOBAL_TAG_FILE`      |
| Global tags                | A comma-separated list of tags to apply to all metrics, events, and service checks.                                                                                                                                                         | `DATADOG_JENKINS_PLUGIN_GLOBAL_TAGS`          |
| Global job tags            | A comma separated list of regex to match a job and a list of tags to apply to that job. **Note**: Tags can reference match groups in the regex using the `$` symbol, for example: `(.*?)_job_(*?)_release, owner:$1, release_env:$2, optional:Tag3` | `DATADOG_JENKINS_PLUGIN_GLOBAL_JOB_TAGS`      |
| Send security audit events | Submits the `Security Events Type` of events and metrics (enabled by default).                                                                                                                                                                | `DATADOG_JENKINS_PLUGIN_EMIT_SECURITY_EVENTS` |
| Send system events         | Submits the `System Events Type` of events and metrics (enabled by default).                                                                                                                                                                  | `DATADOG_JENKINS_PLUGIN_EMIT_SYSTEM_EVENTS`   |
| Enable Log Collection      | Collect and Submit build logs (disabled by default).                                                                                                                                                                  | `DATADOG_JENKINS_PLUGIN_COLLECT_BUILD_LOGS`   |

### Job customization

From a job specific configuration page:

| Customization                         | Description                                                                                                                                                                                           |
|---------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Custom tags                           | Set from a `File` in the job workspace (not compatible with pipeline jobs) or as text `Properties` directly from the configuration page. If set, this overrides the `Global Job Tags` configuration. |
| Send source control management events | Submits the `Source Control Management Events Type` of events and metrics (enabled by default).                                                                                                         |

## Data collected

This plugin is collecting the following [events](#events), [metrics](#metrics), and [service checks](#service-checks):

### Events

#### Default events type

| Event name      | Triggered on              | Default tags                                                              | Associated RATE metric  |
|-----------------|---------------------------|---------------------------------------------------------------------------|-------------------------|
| Build started   | `RunListener#onStarted`   | `branch`, `event_type`, `jenkins_url`, `job`, `node`, `user_id`           | `jenkins.job.started`   |
| Build aborted   | `RunListener#onDeleted`   | `branch`, `event_type`, `jenkins_url`, `job`, `node`, `user_id`           | `jenkins.job.aborted`   |
| Build completed | `RunListener#onCompleted` | `branch`, `event_type`, `jenkins_url`, `job`, `node`, `result`, `user_id` | `jenkins.job.completed` |
| SCM checkout    | `SCMListener#onCheckout`  | `branch`, `event_type`, `jenkins_url`, `job`, `node`, `user_id`           | `jenkins.scm.checkout`  |

NOTE: `event_type` is always set to `default` for above events and metrics.

#### Systems events type

| Event name                   | Triggered on                            | Default tags                                                            | Associated RATE metric                 |
|------------------------------|-----------------------------------------|-------------------------------------------------------------------------|----------------------------------------|
| Computer Online              | `ComputerListener#onOnline`             | `event_type`, `jenkins_url`, `node_hostname`, `node_name`, `node_label` | `jenkins.computer.online`              |
| Computer Offline             | `ComputerListener#onOffline`            | `event_type`, `jenkins_url`, `node_hostname`, `node_name`, `node_label` | `jenkins.computer.offline`             |
| Computer TemporarilyOnline   | `ComputerListener#onTemporarilyOnline`  | `event_type`, `jenkins_url`, `node_hostname`, `node_name`, `node_label` | `jenkins.computer.temporarily_online`  |
| Computer TemporarilyOffline  | `ComputerListener#onTemporarilyOffline` | `event_type`, `jenkins_url`, `node_hostname`, `node_name`, `node_label` | `jenkins.computer.temporarily_offline` |
| Computer LaunchFailure       | `ComputerListener#onLaunchFailure`      | `event_type`, `jenkins_url`, `node_hostname`, `node_name`, `node_label` | `jenkins.computer.launch_failure`      |
| Item Created                 | `ItemListener#onCreated`                | `event_type`, `jenkins_url`, `user_id`                                  | `jenkins.item.created`                 |
| Item Deleted                 | `ItemListener#onDeleted`                | `event_type`, `jenkins_url`, `user_id`                                  | `jenkins.item.deleted`                 |
| Item Updated                 | `ItemListener#onUpdated`                | `event_type`, `jenkins_url`, `user_id`                                  | `jenkins.item.updated`                 |
| Item Copied                  | `ItemListener#onCopied`                 | `event_type`, `jenkins_url`, `user_id`                                  | `jenkins.item.copied`                  |
| Item Location Changed        | `ItemListener#onLocationChanged`        | `event_type`, `jenkins_url`, `user_id`                                  | `jenkins.item.location_changed`        |
| Config Changed               | `SaveableListener#onChange`             | `event_type`, `jenkins_url`, `user_id`                                  | `jenkins.config.changed`               |

NOTE: `event_type` is always set to `system` for above events and metrics.

#### Security events type

| Event name                  | Triggered on                            | Default tags                                     | Associated RATE metric       |
|-----------------------------|-----------------------------------------|--------------------------------------------------|------------------------------|
| User Authenticated          | `SecurityListener#authenticated`        | `event_type`, `jenkins_url`, `user_id`           | `jenkins.user.authenticated` |
| User failed To Authenticate | `SecurityListener#failedToAuthenticate` | `event_type`, `jenkins_url`, `user_id`           | `jenkins.user.access_denied` |
| User loggedOut              | `SecurityListener#loggedOut`            | `event_type`, `jenkins_url`, `user_id`           | `jenkins.user.logout`        |

NOTE: `event_type` is always set to `security` for above events and metrics.

### Metrics

| Metric Name                            | Description                                                    | Default Tags                                                |
|----------------------------------------|----------------------------------------------------------------|-------------------------------------------------------------|
| `jenkins.computer.launch_failure`      | Rate of computer launch failures.                              | `jenkins_url`                                               |
| `jenkins.computer.offline`             | Rate of computer going offline.                                | `jenkins_url`                                               |
| `jenkins.computer.online`              | Rate of computer going online.                                 | `jenkins_url`                                               |
| `jenkins.computer.temporarily_offline` | Rate of computer going temporarily offline.                    | `jenkins_url`                                               |
| `jenkins.computer.temporarily_online`  | Rate of computer going temporarily online.                     | `jenkins_url`                                               |
| `jenkins.config.changed`               | Rate of configs being changed.                                 | `jenkins_url`, `user_id`                                    |
| `jenkins.executor.count`               | Executor count.                                                | `jenkins_url`, `node_hostname`, `node_name`, `node_label`   |
| `jenkins.executor.free`                | Number of unused executor.                                     | `jenkins_url`, `node_hostname`, `node_name`, `node_label`   |
| `jenkins.executor.in_use`              | Number of idle executor.                                       | `jenkins_url`, `node_hostname`, `node_name`, `node_label`   |
| `jenkins.item.copied`                  | Rate of items being copied.                                    | `jenkins_url`, `user_id`                                    |
| `jenkins.item.created`                 | Rate of items being created.                                   | `jenkins_url`, `user_id`                                    |
| `jenkins.item.deleted`                 | Rate of items being deleted.                                   | `jenkins_url`, `user_id`                                    |
| `jenkins.item.location_changed`        | Rate of items being moved.                                     | `jenkins_url`, `user_id`                                    |
| `jenkins.item.updated`                 | Rate of items being updated.                                   | `jenkins_url`, `user_id`                                    |
| `jenkins.job.aborted`                  | Rate of aborted jobs.                                          | `branch`, `jenkins_url`, `job`, `node`, `user_id`           |
| `jenkins.job.completed`                | Rate of completed jobs.                                        | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id` |
| `jenkins.job.cycletime`                | Build Cycle Time.                                              | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id` |
| `jenkins.job.duration`                 | Build duration (in seconds).                                   | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id` |
| `jenkins.job.feedbacktime`             | Feedback time from code commit to job failure.                 | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id` |
| `jenkins.job.leadtime`                 | Build Lead Time.                                               | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id` |
| `jenkins.job.mtbf`                     | MTBF, time between last successful job and current failed job. | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id` |
| `jenkins.job.mttr`                     | MTTR: time between last failed job and current successful job. | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id` |
| `jenkins.job.started`                  | Rate of started jobs.                                          | `branch`, `jenkins_url`, `job`, `node`, `user_id`           |
| `jenkins.job.waiting`                  | Time spent waiting for job to run (in milliseconds).           | `branch`, `jenkins_url`, `job`, `node`, `user_id`           |
| `jenkins.node.count`                   | Total number of node.                                          | `jenkins_url`                                               |
| `jenkins.node.offline`                 | Offline nodes count.                                           | `jenkins_url`                                               |
| `jenkins.node.online`                  | Online nodes count.                                            | `jenkins_url`                                               |
| `jenkins.plugin.count`                 | Plugins count.                                                 | `jenkins_url`                                               |
| `jenkins.project.count`                | Project count.                                                 | `jenkins_url`                                               |
| `jenkins.queue.size`                   | Queue Size.                                                    | `jenkins_url`                                               |
| `jenkins.queue.buildable`              | Number of Buildable item in Queue.                             | `jenkins_url`                                               |
| `jenkins.queue.pending`                | Number of Pending item in Queue.                               | `jenkins_url`                                               |
| `jenkins.queue.stuck`                  | Number of Stuck item in Queue.                                 | `jenkins_url`                                               |
| `jenkins.queue.blocked`                | Number of Blocked item in Queue.                               | `jenkins_url`                                               |
| `jenkins.scm.checkout`                 | Rate of SCM checkouts.                                         | `branch`, `jenkins_url`, `job`, `node`, `user_id`           |
| `jenkins.user.access_denied`           | Rate of users failing to authenticate.                         | `jenkins_url`, `user_id`                                    |
| `jenkins.user.authenticated`           | Rate of users authenticating.                                  | `jenkins_url`, `user_id`                                    |
| `jenkins.user.logout`                  | Rate of users logging out.                                     | `jenkins_url`, `user_id`                                    |

### Service checks

Build status `jenkins.job.status` with the default tags: : `jenkins_url`, `job`, `node`, `result`, `user_id`

## Issue Tracking

GitHub's built-in issue tracking system is used to track all issues relating to this plugin: [jenkinsci/datadog-plugin/issues][7]. 
However, given how Jenkins plugins are hosted, there may be issues that are posted to JIRA as well. You can check [this jenkins issue][8] for those issue postings.

**Note**: [Unresolved issues on JIRA mentioning Datadog][9].

## Changes

See the [CHANGELOG.md][10].

## How to contribute code

First of all and most importantly, **thank you** for sharing.  

Checkout the [contributing guidelines][11] before you submit an issue or a pull request.  
Checkout the [development document][12] for tips on spinning up a quick development environment locally.


[1]: https://plugins.jenkins.io/datadog
[2]: http://updates.jenkins-ci.org/download/war/1.632/jenkins.war
[3]: https://wiki.jenkins-ci.org/display/JENKINS/Plugins#Plugins-Howtoinstallplugins
[4]: https://app.datadoghq.com/account/settings#api
[5]: https://github.com/jenkinsci/docker
[6]: https://wiki.jenkins-ci.org/display/JENKINS/Logging
[7]: https://github.com/jenkinsci/datadog-plugin/issues
[8]: https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20%28Open%2C%20%22In%20Progress%22%2C%20Reopened%29%20AND%20component%20%3D%20datadog-plugin%20ORDER%20BY%20updated%20DESC%2C%20priority%20DESC%2C%20created%20ASC
[9]: https://issues.jenkins-ci.org/browse/INFRA-305?jql=status%20in%20%28Open%2C%20%22In%20Progress%22%2C%20Reopened%2C%20Verified%2C%20Untriaged%2C%20%22Fix%20Prepared%22%29%20AND%20text%20~%20%22datadog%22
[10]: https://github.com/jenkinsci/datadog-plugin/blob/master/CHANGELOG.md
[11]: https://github.com/jenkinsci/datadog-plugin/blob/master/CONTRIBUTING.md
[12]: https://github.com/jenkinsci/datadog-plugin/blob/master/DEVELOPMENT.md
[13]: https://docs.datadoghq.com/agent/logs/?tab=tcpudp#custom-log-collection
