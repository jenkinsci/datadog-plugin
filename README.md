# Jenkins Datadog Plugin

A Jenkins plugin for automatically forwarding metrics, events, and service checks to a Datadog account.

![Jenkins Datadog Dashboard][16]

**Note**: The [Jenkins CI plugin page][1] for this plugin references this documentation.

## Setup

### Installation

_This plugin requires [Jenkins 2.346.1][2]._

_For older versions of Jenkins (i.e 1.632+), you can find the 1.2.0 version of the plugin [here](https://updates.jenkins.io/download/plugins/datadog/)._

This plugin can be installed from the [Update Center][3] (found at `Manage Jenkins -> Manage Plugins`) in your Jenkins installation:

1. Select the `Available` tab, search for `Datadog`, and select the checkbox next to `Datadog Plugin`.
2. Install the plugin by using one of the two install buttons at the bottom of the screen.
3. To verify the plugin is installed, search for `Datadog Plugin` on the `Installed` tab.

  Continue below for configuration.

**Note**: If you see an unexpected version of the `Datadog Plugin`, run `Check Now` from the `Manage Jenkins -> Manage Plugins` screen.

### Configuration

There are two ways to configure your plugin to submit data to Datadog:

* Using a Datadog Agent that acts as a forwarder between Jenkins and Datadog (recommended).
  - When using a DogStatsD server instead of a full Datadog Agent, only metrics and events are supported.
  - For data submitted from an external host, the Datadog Agent requires the following configuration: `dogstatsd_non_local_traffic: true` and `apm_non_local_traffic: true`. This can be configured using the `datadog.yaml` [configuration file][17].
* Sending data directly to Datadog through HTTP.
  - The HTTP client implementation used is blocking with a timeout duration of 1 minute. If there is a connection problem with Datadog, it may slow your Jenkins instance down.

The configuration can be done from the [plugin user interface](#plugin-user-interface) with a [Groovy script](#groovy-script), or through [environment variables](#environment-variables).

#### Plugin user interface

To configure your Datadog Plugin, navigate to the `Manage Jenkins -> Configure System` page on your Jenkins installation. Once there, scroll down to find the `Datadog Plugin` section:

##### HTTP forwarding

1. Select the radio button next to **Use Datadog API URL and Key to report to Datadog** (selected by default).
2. Paste your [Datadog API key][4] in the `API Key` textbox on the Jenkins configuration screen. If you would like to store your API key with the [Credentails Manager][18], create a Credential for the API key and select that credential in the `Datadog API Key (Select from Credentials)` dropdown.
3. Test your Datadog API key by using the `Test Key` button on the Jenkins configuration screen directly below the API key textbox.
4. (optional) Enter the hostname of the Jenkins server in the Advanced tab to include it with the events.
5. (optional) Enter your [Datadog Log Intake URL][15] and select "Enable Log Collection" in the Advanced tab.
6. (optional) Select "Enable CI Visibility", optionally configuring your CI Instance name.
7. Save your configuration.

##### Datadog Agent forwarding

1. Select the radio button next to **Use the Datadog Agent to report to Datadog**.
2. Specify your Datadog Agent `hostname` and `port`.
3. (optional) Enter the hostname of the Jenkins server in the Advanced tab to include it with the events.
4. (optional) Enter your Log Collection Port, configure [log collection](#log-collection-for-agents) in the Datadog Agent, and select "Enable Log Collection".
5. (optional) Enter your Trace Collection Port and select "Enable CI Visibility", optionally configuring your CI Instance name.
6. Save your configuration.

#### Groovy script

Configure your Datadog plugin to forward data through HTTP or DogStatsD using the Groovy scripts below. Configuring the plugin this way might be useful if you're running your Jenkins Master in a Docker container using the [official Jenkins Docker image][5] or any derivative that supports `plugins.txt` and Groovy init scripts.

##### HTTP forwarding using Groovy

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
d.setExcluded('job1,job2')

// If you want to collect logs
d.setLogIntakeUrl('https://http-intake.logs.datadoghq.com/v1/input/')

// Save config
d.save()
```

##### Datadog Agent forwarding using Groovy

```groovy
import jenkins.model.*
import org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration

def j = Jenkins.getInstance()
def d = j.getDescriptor("org.datadog.jenkins.plugins.datadog.DatadogGlobalConfiguration")

d.setReportWith('DSD')
d.setTargetHost('localhost')
d.setTargetPort(8125)

// If you want to collect logs
d.setTargetLogCollectionPort(10518)
d.setCollectBuildLogs(true)

// If you want to enable CI Visibility
d.setTargetTraceCollectionPort(8126)
d.setEnableCiVisibility(true)
d.setCiInstanceName("jenkins")

// Customization, see dedicated section below
d.setExcluded('job1,job2')

// Save config
d.save()
```

#### Environment variables

Configure your Datadog plugin using environment variables with the `DATADOG_JENKINS_PLUGIN_REPORT_WITH` variable, which specifies the report mechanism to use.

##### HTTP forwarding using environment variables

1. Set the `DATADOG_JENKINS_PLUGIN_REPORT_WITH` variable to `HTTP`.
2. Set the `DATADOG_JENKINS_PLUGIN_TARGET_API_URL` variable, which specifies the Datadog API endpoint (defaults to `https://api.datadoghq.com/api/`).
3. Set the `DATADOG_JENKINS_PLUGIN_TARGET_API_KEY` variable, which specifies your [Datadog API key][4].
4. (optional) Log Collection:
  - Set the `DATADOG_JENKINS_PLUGIN_COLLECT_BUILD_LOGS` variable to `true` in order to enable log collection (disabled by default).
  - Set the `DATADOG_JENKINS_PLUGIN_TARGET_LOG_INTAKE_URL` variable, which specifies the Datadog Log Intake URL (defaults to `https://http-intake.logs.datadoghq.com/v1/input/`).
5. (optional) CI Visibility (trace collection):
  - Set the `DATADOG_JENKINS_PLUGIN_ENABLE_CI_VISIBILITY` variable to `true` in order to enable CI Visibility (disabled by default).
  - Set the `DATADOG_JENKINS_TARGET_WEBHOOK_INTAKE_URL` variable, which specifies the Datadog Webhook Intake URL (defaults to `https://webhook-intake.datadoghq.com/api/v2/webhook/`).
  - Set the `DATADOG_JENKINS_PLUGIN_CI_VISIBILITY_CI_INSTANCE_NAME` variable, which specifies the name of the Jenkins instance for CI Visibility (defaults to `jenkins`).

##### Datadog Agent forwarding using environment variables

1. Set the `DATADOG_JENKINS_PLUGIN_REPORT_WITH` variable to `DSD`.
2. Set the `DATADOG_JENKINS_PLUGIN_TARGET_HOST` variable, which specifies the DogStatsD server host (defaults to `localhost`).
3. Set the `DATADOG_JENKINS_PLUGIN_TARGET_PORT` variable, which specifies the DogStatsD server port (defaults to `8125`).
4. (optional) Log Collection:
   -  Enable [log collection](#log-collection-for-agents) in the Datadog Agent.
   - Set the `DATADOG_JENKINS_PLUGIN_COLLECT_BUILD_LOGS` variable to `true` in order to enable log collection (disabled by default).
   - Set the `DATADOG_JENKINS_PLUGIN_TARGET_LOG_COLLECTION_PORT` variable, which specifies the Datadog Agent log collection port.
5. (optional) CI Visibility (trace collection): 
   - Set the `DATADOG_JENKINS_PLUGIN_ENABLE_CI_VISIBILITY` variable to `true` in order to enable CI Visibility (disabled by default).
   - Set the `DATADOG_JENKINS_PLUGIN_TARGET_TRACE_COLLECTION_PORT` variable, which specifies the Datadog Agent trace collection port (defaults to `8126`).
   - Set the `DATADOG_JENKINS_PLUGIN_CI_VISIBILITY_CI_INSTANCE_NAME` variable, which specifies the name of the Jenkins instance for CI Visibility (defaults to `jenkins`).

Additionally, you can use the standard Datadog environment variables:
   - Set the `DD_AGENT_HOST` variable, which specifies the Datadog Agent host.
   - Set the `DD_AGENT_PORT` variable, which specifies the DogStatsD server port.
   - Set the `DD_TRACE_AGENT_PORT` variable, which specifies the Datadog Agent trace collection port.
   - Set the `DD_TRACE_AGENT_URL` variable, which specifies the Datadog Agent URL to send traces. When set this takes precedence over `DD_AGENT_HOST` and `DD_TRACE_AGENT_PORT`.

The environment variables with the `DATADOG_JENKINS_PLUGIN` namespace take precedence over the standard Datadog environment variables. 

#### Logging

Logging is done by utilizing the `java.util.Logger`, which follows the [best logging practices for Jenkins][6]. To obtain logs, follow the directions in the [Jenkins logging documentation][6]. When adding a logger, all Datadog plugin functions start with `org.datadog.jenkins.plugins.datadog.` and the function name you are after should autopopulate. As of this writing, the only function available was `org.datadog.jenkins.plugins.datadog.listeners.DatadogBuildListener`.

## Customization

### Pipeline customization

The Datadog plugin adds a `datadog` step that provides some configuration option for your pipeline-based jobs.

| Option (type)              | Description                                                                 |
|----------------------------|-----------------------------------------------------------------------------|
| `collectLogs` (`boolean`)  | When log collection is disabled globally, this enables it for the pipeline. |
| `tags` (`String[]`)        | A list of tags to attach to all the data collected about the pipeline.      |

In declarative pipelines, add the step to a top-level `options` block like so:

```groovy
pipeline {
    agent any
    options {
        datadog(collectLogs: true, tags: ["foo:bar", "bar:baz"])
    }
    stages {
        stage('Example') {
            steps {
                echo "Hello world."
            }
        }
    }
}
```

In scripted pipeline, wrap the relevant section with the datadog step like so:

```groovy
datadog(collectLogs: true, tags: ["foo:bar", "bar:baz"]) {
  node {
    stage('Example') {
      echo "Hello world."
    }
  }
}
```

**Note**: Pipeline customizations are only registered after a job has started. Tags specified in pipeline customization will not be associated with `jenkins.job.started`.

### Global customization

To customize your global configuration, in Jenkins navigate to `Manage Jenkins -> Configure System` then click the **Advanced** button. The following options are available:

| Customization              | Description                                                                                                                                                                                                                                 | Environment variable                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| Hostname                   | A hostname to use in every event sent to Datadog.                                                                                                                                                                                           | `DATADOG_JENKINS_PLUGIN_HOSTNAME`             |
| Excluded jobs              | A comma-separated list of regex used to exclude job names from monitoring, for example: `susans-job,johns-.*,prod_folder/prod_release`.                                                                                                      | `DATADOG_JENKINS_PLUGIN_EXCLUDED`            |
| Included jobs              | A comma-separated list of regex used to include job names for monitoring, for example: `susans-job,johns-.*,prod_folder/prod_release`.                                                                                                          | `DATADOG_JENKINS_PLUGIN_INCLUDED`            |
| Global tag file            | The path to a workspace file containing a comma separated list of tags (not compatible with pipeline jobs).                                                                                                                                   | `DATADOG_JENKINS_PLUGIN_GLOBAL_TAG_FILE`      |
| Global tags                | A comma-separated list of tags to apply to all metrics, events, and service checks. Tags can include environment variables that are defined in the master jenkins instance.                                                                                                                                                          | `DATADOG_JENKINS_PLUGIN_GLOBAL_TAGS`          |
| Global job tags            | A comma separated list of regex to match a job and a list of tags to apply to that job. Tags can include environment variables that are defined in the master jenkins instance. **Note**: Tags can reference match groups in the regex using the `$` symbol, for example: `(.*?)_job_(*?)_release, owner:$1, release_env:$2, optional:Tag3` | `DATADOG_JENKINS_PLUGIN_GLOBAL_JOB_TAGS`      |
| Send security audit events | Submits the `Security Events Type` of events and metrics (enabled by default).                                                                                                                                                                | `DATADOG_JENKINS_PLUGIN_EMIT_SECURITY_EVENTS` |
| Send system events         | Submits the `System Events Type` of events and metrics (enabled by default).                                                                                                                                                                  | `DATADOG_JENKINS_PLUGIN_EMIT_SYSTEM_EVENTS`   |

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

| Metric Name                            | Description                                                    | Default Tags                                                               |
|----------------------------------------|----------------------------------------------------------------|----------------------------------------------------------------------------|
| `jenkins.computer.launch_failure`      | Rate of computer launch failures.                              | `jenkins_url`                                                              |
| `jenkins.computer.offline`             | Rate of computer going offline.                                | `jenkins_url`                                                              |
| `jenkins.computer.online`              | Rate of computer going online.                                 | `jenkins_url`                                                              |
| `jenkins.computer.temporarily_offline` | Rate of computer going temporarily offline.                    | `jenkins_url`                                                              |
| `jenkins.computer.temporarily_online`  | Rate of computer going temporarily online.                     | `jenkins_url`                                                              |
| `jenkins.config.changed`               | Rate of configs being changed.                                 | `jenkins_url`, `user_id`                                                   |
| `jenkins.executor.count`               | Executor count.                                                | `jenkins_url`, `node_hostname`, `node_name`, `node_label`                  |
| `jenkins.executor.free`                | Number of unused executor.                                     | `jenkins_url`, `node_hostname`, `node_name`, `node_label`                  |
| `jenkins.executor.in_use`              | Number of idle executor.                                       | `jenkins_url`, `node_hostname`, `node_name`, `node_label`                  |
| `jenkins.item.copied`                  | Rate of items being copied.                                    | `jenkins_url`, `user_id`                                                   |
| `jenkins.item.created`                 | Rate of items being created.                                   | `jenkins_url`, `user_id`                                                   |
| `jenkins.item.deleted`                 | Rate of items being deleted.                                   | `jenkins_url`, `user_id`                                                   |
| `jenkins.item.location_changed`        | Rate of items being moved.                                     | `jenkins_url`, `user_id`                                                   |
| `jenkins.item.updated`                 | Rate of items being updated.                                   | `jenkins_url`, `user_id`                                                   |
| `jenkins.job.aborted`                  | Rate of aborted jobs.                                          | `branch`, `jenkins_url`, `job`, `node`, `user_id`                          |
| `jenkins.job.build_duration`           | Build duration without pause (in seconds).                     | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id`                |
| `jenkins.job.completed`                | Rate of completed jobs.                                        | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id`                |
| `jenkins.job.cycletime`                | Build Cycle Time.                                              | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id`                |
| `jenkins.job.duration`                 | Build duration (in seconds).                                   | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id`                |
| `jenkins.job.feedbacktime`             | Feedback time from code commit to job failure.                 | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id`                |
| `jenkins.job.leadtime`                 | Build Lead Time.                                               | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id`                |
| `jenkins.job.mtbf`                     | MTBF, time between last successful job and current failed job. | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id`                |
| `jenkins.job.mttr`                     | MTTR: time between last failed job and current successful job. | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id`                |
| `jenkins.job.pause_duration`            | Pause duration of build job (in seconds).                     | `branch`, `jenkins_url`, `job`, `node`, `result`, `user_id`                |
| `jenkins.job.started`                  | Rate of started jobs.                                          | `branch`, `jenkins_url`, `job`, `node`, `user_id`                          |
| `jenkins.job.stage_duration`           | Duration of individual stages.                                 | `jenkins_url`, `job`, `user_id`, `stage_name`, `stage_depth`, `stage_parent`, `result` |
| `jenkins.job.stage_pause_duration`     | Pause duration of individual stages (in milliseconds).         | `jenkins_url`, `job`, `user_id`, `stage_name`, `stage_depth`, `stage_parent`, `result` |
| `jenkins.job.stage_completed`          | Rate of completed stages.                                      | `jenkins_url`, `job`, `user_id`, `stage_name`, `stage_depth`, `stage_parent`, `result` |
| `jenkins.job.waiting`                  | Time spent waiting for job to run (in milliseconds).           | `branch`, `jenkins_url`, `job`, `node`, `user_id`                          |
| `jenkins.node.count`                   | Total number of node.                                          | `jenkins_url`                                                              |
| `jenkins.node.offline`                 | Offline nodes count.                                           | `jenkins_url`                                                              |
| `jenkins.node.online`                  | Online nodes count.                                            | `jenkins_url`                                                              |
| `jenkins.node_status.count`            | If this node is present.                                       | `jenkins_url`, `node_hostname`, `node_name`, `node_label`                  |
| `jenkins.node_status.up`               | If a given node is online, value 1. Otherwise, 0.              | `jenkins_url`, `node_hostname`, `node_name`, `node_label`                  |
| `jenkins.plugin.count`                 | Plugins count.                                                 | `jenkins_url`                                                              |
| `jenkins.plugin.active`                | Plugins active.                                                | `jenkins_url`                                                              |
| `jenkins.plugin.failed`                | Plugins failed.                                                | `jenkins_url`                                                              |
| `jenkins.plugin.inactivate`            | Plugins inactive.                                              | `jenkins_url`                                                              |
| `jenkins.plugin.withUpdate`            | Plugins with update.                                           | `jenkins_url`                                                              |
| `jenkins.project.count`                | Project count.                                                 | `jenkins_url`                                                              |
| `jenkins.queue.size`                   | Queue Size.                                                    | `jenkins_url`                                                              |
| `jenkins.queue.buildable`              | Number of Buildable item in Queue.                             | `jenkins_url`                                                              |
| `jenkins.queue.pending`                | Number of Pending item in Queue.                               | `jenkins_url`                                                              |
| `jenkins.queue.stuck`                  | Number of Stuck item in Queue.                                 | `jenkins_url`                                                              |
| `jenkins.queue.blocked`                | Number of Blocked item in Queue.                               | `jenkins_url`                                                              |
| `jenkins.queue.job.in_queue`                   | Number of times a Job has been in a Queue.                                                     | `jenkins_url`, `job_name`                                               |
| `jenkins.queue.job.buildable`              | Number of times a Job has been Buildable in a Queue.                             | `jenkins_url`, `job_name`                                               |
| `jenkins.queue.job.pending`                | Number of times a Job has been Pending in a Queue.                             | `jenkins_url`, `job_name`                                               |
| `jenkins.queue.job.stuck`                  | Number of times a Job has been Stuck in a Queue.                                  | `jenkins_url`, `job_name`                                               |
| `jenkins.queue.job.blocked`                | Number of times a Job has been Blocked in a Queue.                           | `jenkins_url`, `job_name`                                               |
| `jenkins.scm.checkout`                 | Rate of SCM checkouts.                                         | `branch`, `jenkins_url`, `job`, `node`, `user_id`                          |
| `jenkins.user.access_denied`           | Rate of users failing to authenticate.                         | `jenkins_url`, `user_id`                                                   |
| `jenkins.user.authenticated`           | Rate of users authenticating.                                  | `jenkins_url`, `user_id`                                                   |
| `jenkins.user.logout`                  | Rate of users logging out.                                     | `jenkins_url`, `user_id`                                                   |

#### Log Collection for Agents

**Note**: This configuration only applies to those using the [Datadog Agent configuration](#plugin-user-interface).

1. Collecting logs is disabled by default in the Datadog Agent, enable it in your `datadog.yaml` file:

   ```yaml
   logs_enabled: true
   ```

2. To collect Jenkins logs, create a [custom log source file][13] for your Agent by creating a `conf.yaml` inside `conf.d/jenkins.d` with the following:

    ```yaml
    logs:
      - type: tcp
        port: <PORT>
        service: <SERVICE>
        source: jenkins
    ```

3. In Jenkins, submit the port you specified above as the `Log Collection Port`. You can set this using [environment variables](#environment-variables), a [Groovy script](#groovy-script), or the [Jenkins UI](#plugin-user-interface).

4. [Restart the Agent][14].

### Service checks

Build status `jenkins.job.status` with the default tags: : `jenkins_url`, `job`, `node`, `user_id`

## Issue tracking

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
[2]: http://updates.jenkins-ci.org/download/war/2.346.1/jenkins.war
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
[14]: https://docs.datadoghq.com/agent/guide/agent-commands/#start-stop-and-restart-the-agent
[15]: https://docs.datadoghq.com/logs/log_collection/?tab=http
[16]: https://raw.githubusercontent.com/jenkinsci/datadog-plugin/master/images/dashboard.png
[17]: https://docs.datadoghq.com/developers/dogstatsd/?tab=containeragent#
[18]: https://www.jenkins.io/doc/book/using/using-credentials/
