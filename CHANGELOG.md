Changes
=======
## 9.1.6 / 2025-06-09
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-9.1.5...datadog-9.1.6

* [Fixed] Fix invalid API Key errors when using JCasC. See [#515](https://github.com/jenkinsci/datadog-plugin/pull/515).

## 9.1.5 / 2025-05-06
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-9.1.4...datadog-9.1.5

* [Added] Implement Go auto-instrumentation for Test Optimization [#510](https://github.com/jenkinsci/datadog-plugin/pull/510)

## 9.1.4 / 2025-04-03
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-9.1.3...datadog-9.1.4

* [Fixed] Fix library checkout test when Controller and Agent have different file systems. See [#505](https://github.com/jenkinsci/datadog-plugin/pull/505).
* [Fixed] Fix branch name detection in pull-requests. See [#506](https://github.com/jenkinsci/datadog-plugin/pull/506).

## 9.1.3 / 2025-02-10
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-9.1.2...datadog-9.1.3

* [Fixed] Display running pipelines that did not pass through the build queue. See [#502](https://github.com/jenkinsci/datadog-plugin/pull/502).

## 9.1.2 / 2025-01-29
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-9.1.1...datadog-9.1.2

* [Fixed] Improve accuracy of detecting branch names for repos checked out in detached HEAD state. See [#500](https://github.com/jenkinsci/datadog-plugin/pull/500).

## 9.1.1 / 2025-01-23
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-9.1.0...datadog-9.1.1

* [Fixed] Mark deprecated config fields as transient. See [#498](https://github.com/jenkinsci/datadog-plugin/pull/498).

## 9.1.0 / 2025-01-20
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-9.0.1...datadog-9.1.0

* [Added] Add UI control and env var to configure if Datadog links are shown. See [#495](https://github.com/jenkinsci/datadog-plugin/pull/495).
* [Added] Add a link to Datadog to job details page. See [#447](https://github.com/jenkinsci/datadog-plugin/pull/447).
* [Added] Add validation to include/exlude jobs regular expressions. See [#450](https://github.com/jenkinsci/datadog-plugin/pull/450).
* [Added] Validate HTTP connectivity for Agent traces port. See [#464](https://github.com/jenkinsci/datadog-plugin/pull/464).
* [Added] Use `jenkins.baseline` to reduce bom update mistakes. See [#492](https://github.com/jenkinsci/datadog-plugin/pull/492). Thanks [strangelookingnerd](https://github.com/strangelookingnerd).
* [Fixed] Ensure environment variables have higher priority than plugin configuration stored on disk. See [#496](https://github.com/jenkinsci/datadog-plugin/pull/496).
* [Fixed] Fix Git metadata extraction for multi-branch pipelines. See [#493](https://github.com/jenkinsci/datadog-plugin/pull/493).

## 9.0.1 / 2025-01-13
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-9.0.0...datadog-9.0.1

* [Fixed] Fix freshly-cloned shared library checkout detection. See [#489](https://github.com/jenkinsci/datadog-plugin/pull/489).
* [Fixed] Do not submit running pipelines data until accurate start time is known. See [#490](https://github.com/jenkinsci/datadog-plugin/pull/490).

## 9.0.0 / 2025-01-07
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-8.3.1...datadog-9.0.0

* [Added] [PLINT-589] Add metric origins for jenkins. See [#485](https://github.com/jenkinsci/datadog-plugin/pull/485).
* [Added] Implement automatic Test Optimization instrumentation for Ruby projects. See [#476](https://github.com/jenkinsci/datadog-plugin/pull/476). Thanks [anmarchenko](https://github.com/anmarchenko).
* [Fixed] Add a guard check against stack overflow when initializing BuildData. See [#486](https://github.com/jenkinsci/datadog-plugin/pull/486).
* [Fixed] Do not tag traces with shared libraries Git metadata. See [#483](https://github.com/jenkinsci/datadog-plugin/pull/483).
* [Fixed] Do not use commit SHA as branch name. See [#482](https://github.com/jenkinsci/datadog-plugin/pull/482).
* [Fixed] Refactor global configuration. See [#446](https://github.com/jenkinsci/datadog-plugin/pull/446).

## 8.3.1 / 2024-12-13
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-8.3.0...datadog-8.3.1

* [Fixed] Do not compress traces and logs batches when sending to old EVP proxy. See [#479](https://github.com/jenkinsci/datadog-plugin/pull/479).

## 8.3.0 / 2024-12-09
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-8.2.0...datadog-8.3.0

* [Added] Enable traces batching by default. See [#473](https://github.com/jenkinsci/datadog-plugin/pull/473).
* [Fixed] Fix Datadog pipeline options scope. See [#468](https://github.com/jenkinsci/datadog-plugin/pull/468).
* [Fixed] Fix auto-instrumentation to inject tracer into Gradle Launcher instead of Gradle Daemon. See [#474](https://github.com/jenkinsci/datadog-plugin/pull/474).
* [Fixed] Propagate executor number data from step trace to stage trace and pipeline trace. See [#475](https://github.com/jenkinsci/datadog-plugin/pull/475).

## 8.2.0 / 2024-11-21
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-8.1.0...datadog-8.2.0

* [Fixed] Add NPE checks for jobs started with scripting. See [#471](https://github.com/jenkinsci/datadog-plugin/pull/471).
* [Fixed] Implement batch submission for traces. See [#469](https://github.com/jenkinsci/datadog-plugin/pull/469).

## 8.1.0 / 2024-11-06
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-8.0.0...datadog-8.1.0

* [Added] Add jenkins.plugin.withWarning metric. See [#453](https://github.com/jenkinsci/datadog-plugin/pull/453).
* [Fixed] Only instrument Maven/Gradle/SBT/Ant when doing tests auto-instrumentation. See [#465](https://github.com/jenkinsci/datadog-plugin/pull/465).

## 8.0.0 / 2024-10-31
### Details

There are no changes, earlier 8.0.0-beta is rolled out as a normal release.

## 8.0.0-beta / 2024-10-22
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-7.2.1...datadog-8.0.0-beta

* [Added] Implement diagnostic flare. See [#454](https://github.com/jenkinsci/datadog-plugin/pull/454).
* [Fixed] Refactor logs submission. See [#451](https://github.com/jenkinsci/datadog-plugin/pull/451).
* [Fixed] Delay sending start event for pipelines. See [#461](https://github.com/jenkinsci/datadog-plugin/pull/461).

> [!IMPORTANT]  
> 💥 The Jenkins Configuration as Code (JCasC) attribute `retryLogs` is not supported anymore.
>
> If you use a JCasC YAML configuration, either:
> - Ensure the attribute `unclassified.datadogGlobalConfiguration.retryLogs` is removed
> - Or set `unclassified.datadogGlobalConfiguration.deprecated` to `warn` to avoid the error
> ```text
> Error Loading Configuration 'retryLogs' is deprecated
> ```
>
> See https://github.com/jenkinsci/datadog-plugin/issues/467 for details

## 7.2.1 / 2024-09-24
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-7.2.0...datadog-7.2.1

* [Fixed] Fix custom tracer JAR verification. See [#459](https://github.com/jenkinsci/datadog-plugin/pull/459).

## 7.2.0 / 2024-09-24
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-7.1.3...datadog-7.2.0

* [Added] Refactor Datadog clients. See [#408](https://github.com/jenkinsci/datadog-plugin/pull/408).
* [Fixed] Fix branch name detection for multibranch pipelines. See [#452](https://github.com/jenkinsci/datadog-plugin/pull/452).
* [Fixed] Improve job hostname detection on Windows. See [#448](https://github.com/jenkinsci/datadog-plugin/pull/448).
* [Fixed] Fix deadlock when loading global configuration on startup. See [#449](https://github.com/jenkinsci/datadog-plugin/pull/449).
* [Fixed] Remove restriction on possible hosts for Java tracer URL. See [#456](https://github.com/jenkinsci/datadog-plugin/pull/456).

## 7.1.3 / 2024-08-26
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-7.1.2...datadog-7.1.3

* [Fixed] Fix APM tracer libraries verification. See [#444](https://github.com/jenkinsci/datadog-plugin/pull/444).
* [Fixed] Add validation for manually set Git timestamps. See [#443](https://github.com/jenkinsci/datadog-plugin/pull/443).

## 7.1.2 / 2024-07-12
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-7.1.1...datadog-7.1.2

* [Fixed] Fix setting Datadog API key credentials with configuration as code. See [#438](https://github.com/jenkinsci/datadog-plugin/pull/438).

## 7.1.1 / 2024-06-27
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-7.1.0...datadog-7.1.1

* [Fixed] added extra conditions for counting executors. See [#435](https://github.com/jenkinsci/datadog-plugin/pull/435).
* [Fixed] Consider job inclusion/exclusion setting when tracking logs. See [#432](https://github.com/jenkinsci/datadog-plugin/pull/432).
* [Fixed] Update APM auto-instrumentation to match any Java process. See [#433](https://github.com/jenkinsci/datadog-plugin/pull/433).

## 7.1.0 / 2024-06-04
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-7.0.1...datadog-7.1.0

* [Added] Improve hostname calculation logic. See [#428](https://github.com/jenkinsci/datadog-plugin/pull/428).
* [Fixed] Short-circuit StageData creation in trace generation. See [#430](https://github.com/jenkinsci/datadog-plugin/pull/430). Thanks [msbit01](https://github.com/msbit01).

## 7.0.1 / 2024-05-06
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-7.0.0...datadog-7.0.1

* [Fixed] Fix Additional Settings field help in automatic Test Visibility instrumentation. See [#421](https://github.com/jenkinsci/datadog-plugin/pull/421).
* [Fixed] Fix BuildSpanAction deserialization logic. See [#425](https://github.com/jenkinsci/datadog-plugin/pull/425).
* [Fixed] Fix log spamming for builds that do not have upstream parent. See [#426](https://github.com/jenkinsci/datadog-plugin/pull/426).

## 7.0.0 / 2024-04-30
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-6.0.3...datadog-7.0.0

* Supported Jenkins versions:
  * Jenkins >= 2.361.4

* [Changed] Changed plugin to Java 11. See [#404](https://github.com/jenkinsci/datadog-plugin/pull/404)
* [Added] Implement running jobs metric + update metrics code. See [#407](https://github.com/jenkinsci/datadog-plugin/pull/407).
* [Added] Support linking downstream pipelines to upstream pipelines in CI Visibility. See [#405](https://github.com/jenkinsci/datadog-plugin/pull/405).
* [Added] Implement automatic Test Visibility instrumentation for .NET projects. See [#400](https://github.com/jenkinsci/datadog-plugin/pull/400).
* [Added] Implement submitting in-progress pipelines data. See [#387](https://github.com/jenkinsci/datadog-plugin/pull/387).
* [Added] Tag pipeline spans with plugin version. See [#398](https://github.com/jenkinsci/datadog-plugin/pull/398).
* [Fixed] Fix queue time calculation for builds and pipeline steps. See [#406](https://github.com/jenkinsci/datadog-plugin/pull/406).

## 6.0.3 / 2024-04-11
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-6.0.2...datadog-6.0.3

* [Fixed] Add git-client as a plugin dependency. See [#413](https://github.com/jenkinsci/datadog-plugin/pull/413).

## 6.0.2 / 2024-02-08
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-6.0.1...datadog-6.0.2

Fixes StackOverflowException occurring when logs collection is enabled.

* [Fixed] Fix stack overflow on initialising DatadogTaskListenerDecorator. See [#396](https://github.com/jenkinsci/datadog-plugin/pull/396).

## 6.0.1 / 2024-02-05
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-6.0.0...datadog-6.0.1

Various bugfixes. Fixes plugin data deserialization issue which could lead to build data corruption.

* [Fixed] Apply included/excluded job filters when publishing queue metrics. See [#380](https://github.com/jenkinsci/datadog-plugin/pull/380).
* [Fixed] Fix incorrect hard-coded env var name in Java tracer configurator. See [#382](https://github.com/jenkinsci/datadog-plugin/pull/382).
* [Fixed] Implement plugin data versioning. See [#390](https://github.com/jenkinsci/datadog-plugin/pull/390).
* [Fixed] Fix automatic APM instrumentation for JS tracer. Add diagnostic logging to APM configurators. Update APM instrumentation docs. See [#388](https://github.com/jenkinsci/datadog-plugin/pull/388).
* [Fixed] Update queue metrics to correctly calculate pipeline names. See [#378](https://github.com/jenkinsci/datadog-plugin/pull/378).
* [Fixed] Fix port used for APM track spans submission. See [#386](https://github.com/jenkinsci/datadog-plugin/pull/386).

## 6.0.0 / 2024-01-31
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.6.2...datadog-6.0.0

Reworking CI Visibility logic: changing how the plugin's internal state is stored, and how pipeline steps are submitted to Datadog.

* [Fixed] Update job name calculation logic to correctly set names for builds that live inside folders. See [#383](https://github.com/jenkinsci/datadog-plugin/pull/383).
* [Fixed] Optimise memory consumption, CPU usage and disk writes. See [#381](https://github.com/jenkinsci/datadog-plugin/pull/381).
* [Fixed] Rework CI Visibility spans batching. See [#379](https://github.com/jenkinsci/datadog-plugin/pull/379).

## 5.6.2 / 2024-01-08
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.6.1...datadog-5.6.2

* [Added] Support declarative Test Visibility configuration in pipeline scripts. See [#375](https://github.com/jenkinsci/datadog-plugin/pull/375).

## 5.6.1 / 2023-11-16
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.6.0...datadog-5.6.1

* [Fixed] Add emitConfigChangeEvents option back. See [#373](https://github.com/jenkinsci/datadog-plugin/pull/373).

## 5.6.0 / 2023-11-13
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.5.1...datadog-5.6.0

* [Added] Add ability to granualarly filter events. See [#364](https://github.com/jenkinsci/datadog-plugin/pull/364). Thanks [dawitgirm](https://github.com/dawitgirm).
  - *Note: removes the deprecated `emitConfigChangeEvents` config option, which is added back in `5.6.1`.*
* [Added] Add support for automatic APM tracers configuration. See [#354](https://github.com/jenkinsci/datadog-plugin/pull/354).
* [Fixed] Add fallback logic for job name and build tag in build data. See [#368](https://github.com/jenkinsci/datadog-plugin/pull/368).

## 5.5.1 / 2023-10-05
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.5.0...datadog-5.5.1

* [Fixed] Fix support for setting environment variables such as `GIT_BRANCH` when CI visibility is disabled. See [#361](https://github.com/jenkinsci/datadog-plugin/pull/361).
* [Fixed] Adjust HTTP client configuration and batch HTTP metrics. See [#362](https://github.com/jenkinsci/datadog-plugin/pull/362).
* [Fixed] Fix file descriptor leak in logger reinitialisation logic and print stack trace in severe logs. See [#347](https://github.com/jenkinsci/datadog-plugin/pull/347).
* [Fixed] Update order of hostname detection logic. See [#360](https://github.com/jenkinsci/datadog-plugin/pull/360).
* [Fixed] Move all HTTP calls to a dedicated class and use Jetty HTTP client instead of raw HttpUrlConnection. See [#346](https://github.com/jenkinsci/datadog-plugin/pull/346).
* [Fixed] Fix support for setting environment variables in pipeline stages. See [#356](https://github.com/jenkinsci/datadog-plugin/pull/356).


## 5.5.0 / 2023-08-25
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.4.2...datadog-5.5.0

* [Added] Add option to use AWS instance ID as hostname. See [#345](https://github.com/jenkinsci/datadog-plugin/pull/345).
* [Fixed] Fix error status propagation to take into account catch/catchError/warnError blocks. See [#343](https://github.com/jenkinsci/datadog-plugin/pull/343).
* [Fixed] Look up hostname from controller environment. See [#340](https://github.com/jenkinsci/datadog-plugin/pull/340). Thanks [Vlatombe](https://github.com/Vlatombe).

## 5.4.2 / 2023-07-12
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.4.1...datadog-5.4.2

* [Fixed] Fix [CVE-2023-37944](https://www.jenkins.io/security/advisory/2023-07-12/#SECURITY-3130) and require Overall/Administer permission to access the affected HTTP endpoint. See [#350](https://github.com/jenkinsci/datadog-plugin/pull/350).

## 5.4.1 / 2023-05-24
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.4.0...datadog-5.4.1

* [Fixed] Don't log SEVERE exception if `/bin/hostname` is missing. See [#339](https://github.com/jenkinsci/datadog-plugin/pull/339). Thanks [Vlatombe](https://github.com/Vlatombe).

## 5.4.0 / 2023-03-29
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.3.0...datadog-5.4.0

* [Added] Add is_manual field to pipelines. See [#336](https://github.com/jenkinsci/datadog-plugin/pull/336).
* [Added] Report errors from unstable jobs. See [#333](https://github.com/jenkinsci/datadog-plugin/pull/333).
* [Changed] Do not map "unstable" status to "success". See [#331](https://github.com/jenkinsci/datadog-plugin/pull/331).

## 5.3.0 / 2023-02-15
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.2.0...datadog-5.3.0

* [Fixed] Logs: Fix logs not showing in Jenkins when log collection was enabled. See [#327](https://github.com/jenkinsci/datadog-plugin/pull/327).
* [Fixed] CI Visibility: Fix hostname not being set for worker nodes in freestyle jobs. See [#328](https://github.com/jenkinsci/datadog-plugin/pull/328).
* [Fixed] CI Visibility: Fix hostname resolution in recent Jenkins versions. See [#326](https://github.com/jenkinsci/datadog-plugin/pull/326).
* [Fixed] CI Visibility: Fix typo in username tag. See [#325](https://github.com/jenkinsci/datadog-plugin/pull/325).
* [Changed] CI Visibility: Move user and parameters to the top level for webhooks. See [#329](https://github.com/jenkinsci/datadog-plugin/pull/329).

## 5.2.0 / 2022-12-15
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.1.0-beta-1...datadog-5.2.0

* [Added] Send webhooks via the Agent EVP Proxy when supported. See [#316](https://github.com/jenkinsci/datadog-plugin/pull/316).
* [Fixed] Set span and trace IDs when sending webhooks. See [#317](https://github.com/jenkinsci/datadog-plugin/pull/317).
* [Fixed] Do not drop partial git info when sending webhooks. See [#319](https://github.com/jenkinsci/datadog-plugin/pull/319).

## 5.1.0-beta-1 / 2022-10-28
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-5.0.0...datadog-5.1.0-beta-1

### Changes
* [Added] Add CI Visibility support in Agentless mode. See [#309](https://github.com/jenkinsci/datadog-plugin/pull/309).
* [Added] Add option to not cache build run when calculating pause duration. See [#313](https://github.com/jenkinsci/datadog-plugin/pull/313).
* [Added] Add the default trace agent port to the config. See [#311](https://github.com/jenkinsci/datadog-plugin/pull/311).
* [Fixed] Remove deprecated `java.level` property. See [#306](https://github.com/jenkinsci/datadog-plugin/pull/306). Thanks [basil](https://github.com/basil).

## 5.0.0 / 2022-08-31
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-4.0.0...datadog-5.0.0

### Changes
* [Changed] Make the plugin see data stream last by adjusting decorator order, to avoid logging unmasked data. See [#296](https://github.com/jenkinsci/datadog-plugin/pull/296). Thanks [fengxx](https://github.com/fengxx).
* [Added] Adds the timestamp, datadog.product and ci.pipeline.name tags to logs. See [#297](https://github.com/jenkinsci/datadog-plugin/pull/297)
* [Added] Add hostname info for events executed in Jenkins workers. See [#298](https://github.com/jenkinsci/datadog-plugin/pull/298)
* [Fixed] The pipeline name logic does not require git information. See [#297](https://github.com/jenkinsci/datadog-plugin/pull/297)
* [Fixed] Remove `synchronization` on `ConcurrentHashMaps` for CI Visibility traces. See [#299](https://github.com/jenkinsci/datadog-plugin/pull/299)

* Supported Jenkins versions:
  * Jenkins >= 2.346.1

## 4.1.0-beta-1 / 2022-08-09
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-4.0.0...datadog-4.1.0-beta-1

### Changes
* [Added] Adds the timestamp, datadog.product and ci.pipeline.name tags to logs. See [#297](https://github.com/jenkinsci/datadog-plugin/pull/297)
* [Added] Add hostname info for events executed in Jenkins workers. See [#298](https://github.com/jenkinsci/datadog-plugin/pull/298)
* [Fixed] The pipeline name logic does not require git information. See [#297](https://github.com/jenkinsci/datadog-plugin/pull/297)
* [Fixed] Remove `synchronization` on `ConcurrentHashMaps` for CI Visibility traces. See [#299](https://github.com/jenkinsci/datadog-plugin/pull/299)

## 4.0.0 / 2022-04-27
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.5.2...datadog-4.0.0

* Supported Jenkins versions:
  * Jenkins >= 2.303.3

### Changes
* [Changed] Use `jnr-posix-api` plugin. See [#287](https://github.com/jenkinsci/datadog-plugin/pull/287)

## 3.5.2 / 2022-04-22
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.5.1...datadog-3.5.2

### Changes
* [Fixed] Fix `ConcurrentModificationException` when serializing `PipelineQueueInfoAction`. See [#286](https://github.com/jenkinsci/datadog-plugin/pull/286).
* [Fixed] Use the correct payload buffer to send data for CI Visibility. See [#289](https://github.com/jenkinsci/datadog-plugin/pull/289)

## 3.5.1 / 2022-03-28
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.5.0...datadog-3.5.1

### Changes
* [Fixed] Filter sensitive information in the `git.repository_url` tag. See [#281](https://github.com/jenkinsci/datadog-plugin/pull/281).

## 3.5.0 / 2022-02-23
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.4.1...datadog-3.5.0

### Changes
* [Added] Add option to refresh statsd client when the host's IP has changed. See [#276](https://github.com/jenkinsci/datadog-plugin/pull/276).
* [Fixed] Update `Test Connection` button text to be more clear. See [#274](https://github.com/jenkinsci/datadog-plugin/pull/274).

## 3.4.1 / 2022-01-05
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.4.0...datadog-3.4.1

### Changes
* [Fixed] Ban vulnerable log4j versions. See [#270](https://github.com/jenkinsci/datadog-plugin/pull/270).
* [Fixed] Properly validate credentials API key before using. See [#268](https://github.com/jenkinsci/datadog-plugin/pull/268).
* [Fixed] Accept "0" as a valid port value to support UDS. See [#242](https://github.com/jenkinsci/datadog-plugin/pull/242). Thanks [sa-spag](https://github.com/sa-spag).

## 3.4.0 / 2021-12-09
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.3.0...datadog-3.4.0

### Changes
* [Added] Provide option to use Jenkins Credentials for API key input. See [#255](https://github.com/jenkinsci/datadog-plugin/pull/255).
* [Fixed] Add more error handling on payload creation and add option to not retry sending logs. See [#260](https://github.com/jenkinsci/datadog-plugin/pull/260).
* [Fixed] Send raw `git.repository_url` in Jenkins pipelines. See [#259](https://github.com/jenkinsci/datadog-plugin/pull/259).

## 3.3.0 / 2021-10-25
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.2.0...datadog-3.3.0

### Changes
* [Added] Add `user.email` to the CI Visibility pipeline attributes. See [#253](https://github.com/jenkinsci/datadog-plugin/pull/253)
* [Added] Allow setting Git information via environment variables. See [#252](https://github.com/jenkinsci/datadog-plugin/pull/252)

## 3.2.0 / 2021-09-16
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.1.2...datadog-3.2.0

### Changes
* [Added] Propagate trace/span IDs via environment variables for Jenkins Pipelines. See [#246](https://github.com/jenkinsci/datadog-plugin/pull/246)
* [Fixed] Avoid unnecessary `GitClient` instantiation in CI Pipelines. See [#240](https://github.com/jenkinsci/datadog-plugin/pull/240)
* [Fixed] Fix `enableCiVisibility` property to be use for Jenkins `configuration-as-code` plugin. See [#249](https://github.com/jenkinsci/datadog-plugin/pull/249)

## 3.1.2 / 2021-09-02
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.1.1...datadog-3.1.2

### Changes
* [Fixed] Fix `ConcurrentModificationException` when `StepDataAction` is serialized. See [#243](https://github.com/jenkinsci/datadog-plugin/pull/243)
* [Fixed] Fix stage breakdown to json conversion. See [#241](https://github.com/jenkinsci/datadog-plugin/pull/241)

## 3.1.1 / 2021-08-05
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.1.0...datadog-3.1.1

### Changes
* [Fixed] Avoid sending empty Git info in CI Pipelines. See [#237](https://github.com/jenkinsci/datadog-plugin/pull/237)

## 3.1.0 / 2021-07-27
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.0.2...datadog-3.1.0

### Changes
* [Added] Add support to the standard Datadog Agent environment variables config. See [#235](https://github.com/jenkinsci/datadog-plugin/pull/235)
* [Changed] Remove APM Java Tracer dependency. See [#234](https://github.com/jenkinsci/datadog-plugin/pull/234)

## 3.0.2 / 2021-07-19
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.0.1...datadog-3.0.2

### Changes
* [Fixed] `Test connection` applies only for TCP ports on Datadog Agent transport mode. See [#232](https://github.com/jenkinsci/datadog-plugin/pull/232)

## 3.0.1 / 2021-07-06
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-3.0.0...datadog-3.0.1

### Changes
* [Fixed] Explicit removal of all invisible actions when a pipeline finishes. See [#228](https://github.com/jenkinsci/datadog-plugin/pull/228)

## 3.0.0 / 2021-06-29
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.13.0...datadog-3.0.0

Enabling [CI Visibility](https://docs.datadoghq.com/continuous_integration/) in the Jenkins plugin.

### Changes
* [Added] Enable CI Visibility UI. See [#221](https://github.com/jenkinsci/datadog-plugin/pull/221)
* [Added] Add `_dd.origin` tag to Jenkins traces. See [#218](https://github.com/jenkinsci/datadog-plugin/pull/218)
* [Fixed] Reinitialize Datadog Client when tracer collection port is updated. See [#223](https://github.com/jenkinsci/datadog-plugin/pull/223)  
* [Fixed] Reduce log level for `onDelete` listener method. See [#219](https://github.com/jenkinsci/datadog-plugin/pull/219)

## 2.13.0 / 2021-06-14
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.12.0...datadog-2.13.0

### Changes
* [Added] Add node labels to Jenkins Pipelines traces. See [#212](https://github.com/jenkinsci/datadog-plugin/pull/212)
* [Enhancement] Propagate error tags to all node parents for Jenkins Pipelines traces. See [#213](https://github.com/jenkinsci/datadog-plugin/pull/213)
* [Enhancement] Add best-effort strategy to find the valid Git commit in Jenkins Pipelines traces. See [#214](https://github.com/jenkinsci/datadog-plugin/pull/214)
* [Fixed] Catch NullPointerException in onDeleted method. See [#215](https://github.com/jenkinsci/datadog-plugin/pull/215)

## 2.12.0 / 2021-05-31
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.11.0...datadog-2.12.0

### Changes
* [Added] Add tags configured by the user to Jenkins traces. See [#210](https://github.com/jenkinsci/datadog-plugin/pull/210)

## 2.11.0 / 2021-05-04
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.10.0...datadog-2.11.0

### Changes
* [Added] Add configuration option to disable config change events. See [#201](https://github.com/jenkinsci/datadog-plugin/pull/201).
* [Enhancement] Avoid sending jenkins.step.internal spans. See [#202](https://github.com/jenkinsci/datadog-plugin/pull/202).
* [Fixed] Fix StepData override in DatadogStepListener for Jenkins Pipelines traces. See [#203](https://github.com/jenkinsci/datadog-plugin/pull/203).
* [Fixed] Remove queue time from spans duration on Jenkins traces. See [#204](https://github.com/jenkinsci/datadog-plugin/pull/204).
* [Fixed] Fix node name propagation on Jenkins pipelines traces. See [#205](https://github.com/jenkinsci/datadog-plugin/pull/205)
* [Fixed] Fix ci.status tag value for skipped Stages on Jenkins Pipelines traces. See [#206](https://github.com/jenkinsci/datadog-plugin/pull/206).

## 2.10.0 / 2021-04-12
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.9.0...datadog-2.10.0

### Changes
* [Fixed] Expand error handling for reading tag files. See [#199](https://github.com/jenkinsci/datadog-plugin/pull/199).
* [Removed] Remove the exposure of trace IDs as environment variables. See [#196](https://github.com/jenkinsci/datadog-plugin/pull/196)

## 2.9.0 / 2021-03-24
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.8.3...datadog-2.9.0

### Changes
* [Added] Support environment variables in global tags and global job tags. See [#190](https://github.com/jenkinsci/datadog-plugin/pull/190).
* [Added] Always include purposefully-written messages for severe error cases. See [#187](https://github.com/jenkinsci/datadog-plugin/pull/187).
* [Added] Add stage_pause_duration metric. See [#191](https://github.com/jenkinsci/datadog-plugin/pull/191).
* [Fixed] Make tasklistener decorator serializable. See [#188](https://github.com/jenkinsci/datadog-plugin/pull/188).

## 2.8.3 / 2021-02-22
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.8.2...datadog-2.8.3

### Changes
* [Fixed] Avoid calculating `_dd.hostname` for Jenkins workers. See [#184](https://github.com/jenkinsci/datadog-plugin/pull/184)
* [Enhancement] Improve log message on incompatibilities when `dd-java-agent` is used as `javaagent`. See [#185](https://github.com/jenkinsci/datadog-plugin/pull/185)

## 2.8.2 / 2021-02-16
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.8.1...datadog-2.8.2

### Changes
* [Fixed] Remove branch with escaped characters from `pipeline.name`. See [#181](https://github.com/jenkinsci/datadog-plugin/pull/181)

## 2.8.1 / 2021-02-03
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.8.0...datadog-2.8.1

### Changes
* [Fixed] Fix deadlock if FlowNode cannot be located in DatadogQueueListener. See [#178](https://github.com/jenkinsci/datadog-plugin/pull/178)
* [Fixed] Store `ci.queue_time` as span metric. See [#179](https://github.com/jenkinsci/datadog-plugin/pull/179)

## 2.8.0 / 2021-01-19
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.7.0...datadog-2.8.0

### Changes
* [Fixed] Avoid dropping spans in Jenkins Build/Pipelines traces. See [#173](https://github.com/jenkinsci/datadog-plugin/pull/173)
* [Changed] Update dd-trace-java version to 0.71.0. See [#172](https://github.com/jenkinsci/datadog-plugin/pull/172)
* [Changed] Avoid using Gson instance in Datadog Traces. See [#171](https://github.com/jenkinsci/datadog-plugin/pull/171)

## 2.7.0 / 2021-01-15
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.6.0...datadog-2.7.0

### Changes
* [Added] Configure the default branch using environment variable. See [#168](https://github.com/jenkinsci/datadog-plugin/pull/168)

## 2.6.0 / 2020-12-15
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.5.0...datadog-2.6.0

### Changes
* [Added] Add ci.queue_time for pipeline/stage/job traces. See [#159](https://github.com/jenkinsci/datadog-plugin/pull/159)
* [Added] Add ci.status for Jenkins Build/Pipelines. See [#164](https://github.com/jenkinsci/datadog-plugin/pull/164)
* [Added] Add _dd.ci.level tag to Jenkins Build/Pipelines. See [#165](https://github.com/jenkinsci/datadog-plugin/pull/165)

## 2.5.0 / 2020-12-14
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.4.0...datadog-2.5.0

### Changes
* [Added] Add stage breakdown to Jenkins Build/Pipelines traces.. See [#158](https://github.com/jenkinsci/datadog-plugin/pull/158).
* [Added] Add new plugin metrics . See [#156](https://github.com/jenkinsci/datadog-plugin/pull/156). Thanks [hoshsadiq](https://github.com/hoshsadiq).
* [Added] Add git.default_branch tag to Jenkins Build/Pipeline traces. See [#150](https://github.com/jenkinsci/datadog-plugin/pull/150).
* [Added] Propagate CI parents tags in Jenkins Pipelines traces. See [#149](https://github.com/jenkinsci/datadog-plugin/pull/149).
* [Fixed] Fix stage.name propagation for all children jobs in Jenkins Pipelines. See [#160](https://github.com/jenkinsci/datadog-plugin/pull/160).
* [Fixed] Fix API key form validation. See [#154](https://github.com/jenkinsci/datadog-plugin/pull/154).
* [Fixed] Configure hostname in the tracer. See [#151](https://github.com/jenkinsci/datadog-plugin/pull/151).
* [Changed] Add dec and hex flavour to the environment variable trace IDs.. See [#152](https://github.com/jenkinsci/datadog-plugin/pull/152).

# 2.4.0 / 2020-10-16
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.3.0...datadog-2.4.0

Couple additions to better support stage-level metrics.

### Changes
* [Added] Support jenkins.job.stage_completed. See [#145](https://github.com/jenkinsci/datadog-plugin/pull/145). Thanks [patelronak](https://github.com/patelronak).
* [Added] Add a SKIPPED result status for stages. See [#147](https://github.com/jenkinsci/datadog-plugin/pull/147).

# 2.3.0 / 2020-10-13
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.2.0...datadog-2.3.0

Improvements to APM tracing and reduction in unnecessary BuildAborted events.

### Changes
* [Added] Add commit message, author, and committer info to traces. See [#135](https://github.com/jenkinsci/datadog-plugin/pull/135).
* [Added] Expose trace IDs as environment variables. See [#134](https://github.com/jenkinsci/datadog-plugin/pull/134).
* [Fixed] Avoid sending deleted events for already completed jobs. See [#133](https://github.com/jenkinsci/datadog-plugin/pull/133).
* [Fixed] Change 'CANCELLED' to 'CANCELED' as build result tag. (US spelling). See [#132](https://github.com/jenkinsci/datadog-plugin/pull/132).

# 2.2.0 / 2020-09-17
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.1.1...datadog-2.2.0

Fixes and improvements for APM Traces for Jenkins Builds/Pipelines.

### Changes
* [Added] Service name configurable for traces collection. See [#117](https://github.com/jenkinsci/datadog-plugin/pull/117)
* [Added] Attach logs to APM traces. See [#121](https://github.com/jenkinsci/datadog-plugin/pull/121)
* [Added] Add queue time metric in APM traces as tag. See [#127](https://github.com/jenkinsci/datadog-plugin/pull/127)
* [Fixed] Update several tags for traces in Jenkins pipelines. See [#119](https://github.com/jenkinsci/datadog-plugin/pull/119)
* [Fixed] Fix mismatching between StepData/Step in traces. See [#123](https://github.com/jenkinsci/datadog-plugin/pull/123)
* [Fixed] Update the job name for APM traces. See [#124](https://github.com/jenkinsci/datadog-plugin/pull/124)
* [Fixed] Fix hostname resolution for APM traces in pipeline nodes. See [#125](https://github.com/jenkinsci/datadog-plugin/pull/125)

# 2.1.1 / 2020-08-28
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.1.0...datadog-2.1.1

### Changes
* [Fixed] Prevent exception if traces form data is not present. See [#114](https://github.com/jenkinsci/datadog-plugin/pull/114)
* [Fixed] Fix NPE when traces collection is disabled. See [#115](https://github.com/jenkinsci/datadog-plugin/pull/115)

# 2.1.0 / 2020-08-26
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-2.0.0...datadog-2.1.0

Sending APM traces from Jenkins Builds/Pipelines

### Changes
* [Added] Send APM traces. See [#96](https://github.com/jenkinsci/datadog-plugin/pull/96).
* [Added] Add additional tags to APM traces. See [#111](https://github.com/jenkinsci/datadog-plugin/pull/111).
* [Added] Add job name tag to pipelines in queues. See [#101](https://github.com/jenkinsci/datadog-plugin/pull/101).

# 2.0.0 / 2020-08-18
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-1.1.3...datadog-2.0.0

Adding three new pipeline metrics:
- jenkins.job.pause_duration
- jenkins.job.build_duration
- jenkins.job.stage_duration

Forwarding logs from pipeline-based jobs is now possible.

Introducing a 'datadog' pipeline step to allow configuration from the Jenkinsfile.

### Changes
* [Added] Add a Datadog build step. See [#88](https://github.com/jenkinsci/datadog-plugin/pull/88).
* [Added] Use inclusive naming . See [#94](https://github.com/jenkinsci/datadog-plugin/pull/94).
* [Added] Add new queue metrics with job tags. See [#73](https://github.com/jenkinsci/datadog-plugin/pull/73).
* [Added] Add pipeline pause and build duration. See [#77](https://github.com/jenkinsci/datadog-plugin/pull/77).
* [Added] Collect and submit jenkins.job.stage_duration. See [#76](https://github.com/jenkinsci/datadog-plugin/pull/76).
* [Added] Add new node status metrics . See [#71](https://github.com/jenkinsci/datadog-plugin/pull/71).
* [Added] Add support for collection of pipeline logs. See [#74](https://github.com/jenkinsci/datadog-plugin/pull/74).
* [Added] Add workflow-job dependency. See [#72](https://github.com/jenkinsci/datadog-plugin/pull/72).
* [Fixed] Add result tag to stage metrics. See [#92](https://github.com/jenkinsci/datadog-plugin/pull/92).
* [Fixed] Keep backwards compatibility with old config naming. See [#95](https://github.com/jenkinsci/datadog-plugin/pull/95).
* [Fixed] Use more inclusive naming of config. See [#93](https://github.com/jenkinsci/datadog-plugin/pull/93).
* [Fixed] Don't fail validation if log connection is broken. See [#82](https://github.com/jenkinsci/datadog-plugin/pull/82).
* [Fixed] Re-order configuration loading logic. See [#87](https://github.com/jenkinsci/datadog-plugin/pull/87).
* [Fixed] Update config access through lookupSingleton method. See [#85](https://github.com/jenkinsci/datadog-plugin/pull/85).
* [Fixed] Fix buildable and pending metrics. See [#83](https://github.com/jenkinsci/datadog-plugin/pull/83).
* [Fixed] Update failedlastValidation if client passes validation  . See [#81](https://github.com/jenkinsci/datadog-plugin/pull/81).
* [Fixed] Update pom for 1.x. See [#80](https://github.com/jenkinsci/datadog-plugin/pull/80).
* [Fixed] Add configuration validation in clients. See [#59](https://github.com/jenkinsci/datadog-plugin/pull/59).
* [Fixed] Enforce POST in form validation. See [#61](https://github.com/jenkinsci/datadog-plugin/pull/61).
* [Changed] Remove the result tag from the service check. See [#103](https://github.com/jenkinsci/datadog-plugin/pull/103).

# 1.1.3 / 2020-07-23
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-1.1.2...datadog-1.1.3

### Changes
* [Added] Use inclusive naming. See [#94](https://github.com/jenkinsci/datadog-plugin/pull/94).
* [Added] Add new queue metrics with job tags. See [#73](https://github.com/jenkinsci/datadog-plugin/pull/73).
* [Added] Add new node status metrics. See [#71](https://github.com/jenkinsci/datadog-plugin/pull/71).
* [Fixed] Keep backwards compatibility with old config naming. See [#95](https://github.com/jenkinsci/datadog-plugin/pull/95).
* [Fixed] Don't fail validation if log collection is not working. See [#90](https://github.com/jenkinsci/datadog-plugin/pull/90).
* [Fixed] Re-order configuration loading logic. See [#87](https://github.com/jenkinsci/datadog-plugin/pull/87).
* [Fixed] Fix buildable and pending metrics. See [#83](https://github.com/jenkinsci/datadog-plugin/pull/83).
* [Fixed] Update failedlastValidation if client passes validation. See [#81](https://github.com/jenkinsci/datadog-plugin/pull/81).
* [Fixed] Update pom for 1.x. See [#80](https://github.com/jenkinsci/datadog-plugin/pull/80).
* [Fixed] Add configuration validation in clients. See [#59](https://github.com/jenkinsci/datadog-plugin/pull/59).
* [Fixed] Enforce POST in form validation. See [#61](https://github.com/jenkinsci/datadog-plugin/pull/61).


# 1.1.2 / 06-09-2020
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-1.1.1...datadog-1.1.2

### Changes
* [IMPROVEMENT] Remove debugging log line [#65](https://github.com/jenkinsci/datadog-plugin/pull/65)
* [IMPROVEMENT] Override `getNormalLoggingLevel` to set log `Level.Fine` [#63](https://github.com/jenkinsci/datadog-plugin/pull/63) Thanks [@jetersen](https://github.com/jetersen)
* [IMPROVEMENT] Add minimum jenkins version [#67](https://github.com/jenkinsci/datadog-plugin/pull/67)

# 1.1.1 / 05-04-2020
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-1.1.0...datadog-1.1.1

### Changes
* [IMPROVEMENT] Set hpi.compatibleSinceVersion to 1.0.0 [#58](https://github.com/jenkinsci/datadog-plugin/pull/58)

# 1.1.0 / 04-02-2020
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-1.0.2...datadog-1.1.0

### Changes
* [IMPROVEMENT] Add log collection [#44](https://github.com/jenkinsci/datadog-plugin/pull/44)
* [IMPROVEMENT] Add user_id tag [#46](https://github.com/jenkinsci/datadog-plugin/pull/46)
* [IMPROVEMENT] Add branch and event_type tags [#51](https://github.com/jenkinsci/datadog-plugin/pull/51)
* [BUGFIX] Handle mis-configured logger for log submission with the Datadog agent [#47](https://github.com/jenkinsci/datadog-plugin/pull/47)


# 1.0.2 / 01-27-2020
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-1.0.1...datadog-1.0.2

### Changes
* [IMPROVEMENT] Exception Logging improvements [#24](https://github.com/jenkinsci/datadog-plugin/pull/24)
* [BUGFIX] Fix missing tags on service checks submission [#30](https://github.com/jenkinsci/datadog-plugin/pull/30)

# 1.0.1 / 01-20-2020
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-1.0.0...datadog-1.0.1

### Changes
* [BUGFIX] Bug fix when calling fetching build tags from job properties. See [#17](https://github.com/jenkinsci/datadog-plugin/pull/17)
* [BUGFIX] Switch labels from entry to checkbox. See [#7](https://github.com/DataDog/jenkins-datadog-plugin/pull/7) Thanks [@jsoref](https://github.com/jsoref)

# 1.0.0 / 01-07-2020
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.7.1...datadog-1.0.0

[BACKWARDS INCOMPATIBILITY NOTES]
* Instead of sending `null` as default value for some tags we now send `unknown`.
* Event titles and messages now include additional information. Search queries and monitors may need updating.
* Node tag is added by default.
* Groovy scripts need to be updated (descriptor path changed). See [Configure With A Groovy Script](https://github.com/DataDog/jenkins-datadog-plugin#configure-with-a-groovy-script).
* More configs are available (some got removed, some added). See [Customization](https://github.com/DataDog/jenkins-datadog-plugin#customization).
* Whitelist and blacklist configs now support regex expressions.

### Changes
* [IMPROVEMENT][BREAKING CHANGE] Add more granular statuses (i.e. `Not Built`, etc) to onCompleted event. See [153](https://github.com/DataDog/jenkins-datadog-plugin/pull/153) (Thanks @mbaitelman)
* [IMPROVEMENT][BREAKING CHANGE] Allow multiple values for tags & Support prefix for White/Blacklist & set Node tag by default & Added API + Target URL validations. See [172](https://github.com/DataDog/jenkins-datadog-plugin/pull/172)
* [IMPROVEMENT][BREAKING CHANGE] Improve plugin config. See [177](https://github.com/DataDog/jenkins-datadog-plugin/pull/177)
* [IMPROVEMENT][BREAKING CHANGE] Add full support with DogStatsD client. See [183](https://github.com/DataDog/jenkins-datadog-plugin/pull/183)
* [IMPROVEMENT] Add note about how to send data to EU [154](https://github.com/DataDog/jenkins-datadog-plugin/pull/154)
* [IMPROVEMENT] Send delivery KPIs. See [132](https://github.com/DataDog/jenkins-datadog-plugin/pull/132) & [156](https://github.com/DataDog/jenkins-datadog-plugin/pull/156) (Thanks @pgarbe)
* [IMPROVEMENT] Add thread safety to dogstatsd submissions, adds `jenkins.job.started` and `jenkins.scm.checkout`, and add resiliency around potential build failures. See [169](https://github.com/DataDog/jenkins-datadog-plugin/pull/169)
* [IMPROVEMENT] Add 1 minute request timeout. See [174](https://github.com/DataDog/jenkins-datadog-plugin/pull/174) (Thanks @Mischa-Alff)
* [IMPROVEMENT] Add SVN tag support. See [175](https://github.com/DataDog/jenkins-datadog-plugin/pull/175)
* [IMPROVEMENT] Collect env variables. See [176](https://github.com/DataDog/jenkins-datadog-plugin/pull/176)
* [IMPROVEMENT] Add executor, node, and queue metrics. Also adds total number of jobs metric. See [180](https://github.com/DataDog/jenkins-datadog-plugin/pull/180)
* [IMPROVEMENT] Add slave statistic metrics and more. Also adds security/SCM/system events. See [181](https://github.com/DataDog/jenkins-datadog-plugin/pull/181)
* [IMPROVEMENT] Adding global tag file from workspace. See [182](https://github.com/DataDog/jenkins-datadog-plugin/pull/182)
* [IMPROVEMENT] Allow configuring plugin using env vars. See [184](https://github.com/DataDog/jenkins-datadog-plugin/pull/184)
* [OTHER][BREAKING CHANGE] Overall code refactor and cleanup. See [161](https://github.com/DataDog/jenkins-datadog-plugin/pull/161)
* [OTHER] Lint project and remove unused code. See [160](https://github.com/DataDog/jenkins-datadog-plugin/pull/160)
* [OTHER] Update license and Github files. See [187](https://github.com/DataDog/jenkins-datadog-plugin/pull/187)
* [OTHER] Cleanup license and standard files. See [193](https://github.com/DataDog/jenkins-datadog-plugin/pull/193)

# 0.7.1 / 03-01-2019
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.7.0...datadog-0.7.1

### Changes
* [BUGFIX][IMPROVEMENT] Run only if apiKey is configured, avoid `NullPointerException`'s when getting `apiKey` See [145](https://github.com/DataDog/jenkins-datadog-plugin/pull/145)

# 0.7.0 / 02-25-2019
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.6.5...datadog-0.7.0

### Changes
* [IMPROVEMENT][BREAKING CHANGE] Create events with `alert_type: error` only for jobs with `Result.FAILURE`. For other non-success results create events with `alert_type: warning`. This could potentially break Datadog monitors over Jenkins events. The event `status` maps to the updated `alert_type` modified in this PR. See [140](https://github.com/DataDog/jenkins-datadog-plugin/pull/140) (Thanks @alanranciato)

# 0.6.5 / 11-06-2018
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.6.4...datadog-0.6.5

### Changes
* [BUGFIX] Catch NPE when item.getInQueueSince() is unavailable. See [127](https://github.com/DataDog/jenkins-datadog-plugin/pull/127)
* [OTHER] Update Datadog API endpoint. See [128](https://github.com/DataDog/jenkins-datadog-plugin/pull/128)

# 0.6.4 / 10-22-2018
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.6.3...datadog-0.6.4

### Changes
* [BUGFIX] Set failed event to alert_type error See [124](https://github.com/DataDog/jenkins-datadog-plugin/pull/124)
* [SECURITY] Upgrade httpclient to 4.5.6. See [125](https://github.com/DataDog/jenkins-datadog-plugin/pull/125) and [CVE-2015-5262](https://nvd.nist.gov/vuln/detail/CVE-2015-5262)

# 0.6.3 / 08-07-2018
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.6.2...datadog-0.6.3

### Changes
* [IMPROVEMENT] Add support for global tags (including regexes). See [117](https://github.com/DataDog/jenkins-datadog-plugin/pull/117) (Thanks @nmuesch)
* [IMPROVEMENT] Add node tagging to build start events and job.waiting metric. See [119](https://github.com/DataDog/jenkins-datadog-plugin/pull/119) (Thanks @keirbadger)

# 0.6.2 / 01-11-2018
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.6.1...datadog-0.6.2

### Changes
* [BUGFIX] Don't sanitize whitelist and blacklist. See [#109](https://github.com/DataDog/jenkins-datadog-plugin/pull/109)
* [BUGFIX] Empty whitelist should permit all jobs. See [#106](https://github.com/DataDog/jenkins-datadog-plugin/pull/106) (Thanks @nikola-da)

# 0.6.1 / 08-10-2017
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.5.7...datadog-0.6.1

### Changes
* [IMPROVEMENT] Add metric to measure build waiting time. See [#81](https://github.com/DataDog/jenkins-datadog-plugin/pull/81) (Thanks @bbeck)
* [IMPROVEMENT] Modifies job tag to incorporate subfolder names. This is a potentially breaking change, spaces are now removed from job names. If you are currently monitoring jobs with spaces in the names the tags will no longer have an underscore in place of the space and the job name in events will no longer have a space in the name. See [#68](https://github.com/DataDog/jenkins-datadog-plugin/pull/68) and [#77](https://github.com/DataDog/jenkins-datadog-plugin/pull/77) (Thanks @witokondoria)
* [BUGFIX] Fix duration of pipeline jobs. See [#70](https://github.com/DataDog/jenkins-datadog-plugin/pull/70) (Thanks @ulich, @kitamurakei)
* [IMPROVEMENT] Add whitelist configuration option. This is a potentially breaking change. If you are currently using a blacklist, this may start working differently if you are also using subfolders from the Workflow plugin. The top level job name was being used as the job tag before, and now it is the top level job followed by the subfolder names, separated by a forward slash. So jobs that had subfolders before, but were blacklisted, are going to suddenly appear. See [#78](https://github.com/DataDog/jenkins-datadog-plugin/pull/78), [#88](https://github.com/DataDog/jenkins-datadog-plugin/pull/88) and [#56](https://github.com/DataDog/jenkins-datadog-plugin/pull/56) (Thanks @bhavanki)
* [IMPROVEMENT] Add a metric to measure the size of the build queue. [#82](https://github.com/DataDog/jenkins-datadog-plugin/pull/82) (Thanks @bbeck)
* [BUGFIX] Set tagNode to False by default. See [#84](https://github.com/DataDog/jenkins-datadog-plugin/pull/84)
* [IMPROVEMENT] Lower event priority for non-failure events. See [#86](https://github.com/DataDog/jenkins-datadog-plugin/pull/86)

# 0.5.7 / 08-07-2017
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.5.6...datadog-0.5.7

### Changes
* [SECURITY] Security patch for issue where plugin showed plain text API key in configuration form field. See [Jenkins Security Advisory 2017-08-07](https://jenkins.io/security/advisory/2017-08-07/)

# 0.5.6 / 01-28-2017
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.5.5...datadog-0.5.6

### Changes
* [BUGFIX] Fix memory leak, by stoping the StatsD client after every send. See [#73](https://github.com/DataDog/jenkins-datadog-plugin/pull/73) (Thanks @suxor42)
* [BUGFIX] Include the result tag in the jenkins.job.completed metric. See [#76](https://github.com/DataDog/jenkins-datadog-plugin/pull/76)

# 0.5.5 / 10-18-2016
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.5.4...datadog-0.5.5

### Changes
* [IMPROVEMENT] Add setters to allow for use of Groovy scripts. See [#64](https://github.com/DataDog/jenkins-datadog-plugin/pull/64) (Thanks @jniesen)
* [BUGFIX] Fix string handling by using utf-8. See [#63](https://github.com/DataDog/jenkins-datadog-plugin/pull/63) (Thanks @k_kitamura)
* [BUGFIX] Fix service checks listing separate groups for each result. See [#65](https://github.com/DataDog/jenkins-datadog-plugin/pull/65)

# 0.5.4 / 10-11-2016
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.5.3...datadog-0.5.4

### Changes
* [BUGFIX] Fix tags generation

# 0.5.3 / 07-12-2016
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.5.2...datadog-0.5.3

### Changes
* [BUGFIX] Reintroduce Jenkins source type for all events.

# 0.5.2 / 06-23-2016
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.5.1...datadog-0.5.2

### Changes
* [BUGFIX] Catch and react to null property in DatadogUtilities.parseTagList(). See [84ec03](https://github.com/DataDog/jenkins-datadog-plugin/commit/84ec0385459928d6f408b7e2c0fe215555550da1)

# 0.5.1 / 06-01-2016
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.5.0...datadog-0.5.1

### Changes
* [BUGFIX] Fixed an unhandled NPE caused when DataDog Job Properties were not selected. See [#44](https://github.com/DataDog/jenkins-datadog-plugin/pull/44) (Thanks @MadsNielsen)

# 0.5.0 / 05-24-2016
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.4.1...datadog-0.5.0

### Changes
* [IMPROVEMENT] Adding tags by job, via job configuration screen or via workspace text file. See [#38](https://github.com/DataDog/jenkins-datadog-plugin/pull/38) (Thanks @MadsNielsen)
* [IMPROVEMENT] Count metric for completed jobs. See [#39](https://github.com/DataDog/jenkins-datadog-plugin/pull/39) (Thanks @MadsNielsen)

# 0.4.1 / 12-08-2015
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.4.0...datadog-0.4.1

### Changes
* [BUGFIX] Fixed issue where apiKey was being returned to the configuration form as hash, causing a test of the key to fail. See [ee95325](https://github.com/DataDog/jenkins-datadog-plugin/commit/ee9532532df99ab998e5f7eb171636905aec6f8c)
* [BUGFIX] Removed a false error log, which was reporting successful POSTs as an error. See [094fbe8](https://github.com/DataDog/jenkins-datadog-plugin/commit/094fbe80cc00378d03d2e357e8e9cfc6f04e86ad)
* [BUGFIX] Round job duration text to the nearest 2 decimals on event messages. See [7bdef98](https://github.com/DataDog/jenkins-datadog-plugin/commit/7bdef98260fc2b42b8c041f39cade6ae3fdb37f8)
* [IMPROVEMENT] Reporting all events as Jenkins source type, enabling proper event display. See [f00b261](https://github.com/DataDog/jenkins-datadog-plugin/commit/f00b26165f040e9bd1996bb1f4fb63ff05c1156f)

# 0.4.0 / 12-04-2015
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.3.0...datadog-0.4.0

### Changes
* [IMPROVEMENT] Add support for using a proxy server, utilizing Jenkins proxy settings. See [#30](https://github.com/DataDog/jenkins-datadog-plugin/pull/30) (Thanks @seattletechie)
* [IMPROVEMENT] Replace PrintStream with java.util.Logger, to produce log verbosity control, allowing use of log groups and levels in Jenkins. See [#29](https://github.com/DataDog/jenkins-datadog-plugin/pull/29) (Thanks @dmabamboo)
* [OTHER] Cleaned up blacklist code. See [#28](https://github.com/DataDog/jenkins-datadog-plugin/pull/28) (Thanks @dmabamboo)

# 0.3.0 / 10-19-2015
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-0.2.1...datadog-0.3.0

### Changes
* [IMPROVEMENT] Added the ability to include optional preset tags. See [ea17e44](https://github.com/DataDog/jenkins-datadog-plugin/commit/ea17e44496e5d112196f67c26869969ec15211d4)
* [IMPROVEMENT] Added the ability to blacklist jobs from being reported to DataDog. See [9fde32a](https://github.com/DataDog/jenkins-datadog-plugin/commit/9fde32a699aceaf73de03622147cf39422112197)

# 0.2.1 / 09-25-2015
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-build-reporter-0.2.0...datadog-0.2.1

### Changes
* [BUGFIX] Changed the plugin id from `datadog-build-reporter` to just `datadog`. See [#18](https://github.com/DataDog/jenkins-datadog-plugin/pull/18)

# 0.2.0 / 09-22-2015
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-build-reporter-0.1.3...datadog-build-reporter-0.2.0

### Changes
* [BUGFIX] Improved method of determining the Jenkins hostname. See [#15](https://github.com/DataDog/jenkins-datadog-plugin/pull/15)
* [IMPROVEMENT] Add node tag to events, metrics, and service checks. See [#17](https://github.com/DataDog/jenkins-datadog-plugin/pull/17)
* [OTHER] Remove build_number tag from metrics and service checks. See [#17](https://github.com/DataDog/jenkins-datadog-plugin/pull/17)

# 0.1.3 / 09-04-2015
### Details
https://github.com/jenkinsci/datadog-plugin/compare/datadog-build-reporter-0.1.2...datadog-build-reporter-0.1.3

### Changes
* [BUGFIX] Added a null safe getter function to prevent exceptions when attempting to call `.toString()` on a `null` object. See [#9](https://github.com/DataDog/jenkins-datadog-plugin/pull/9)
* [IMPROVEMENT] Events: Allow for event rollups on Datadog events page.
* [OTHER] Modified build page link to point to the main build page, rather than to the console output.
* [OTHER] Removed build_number tags from events.

# 0.1.2 / 09-01-2015
### Details
Testing automatic release with new Jenkins job.

https://github.com/jenkinsci/datadog-plugin/compare/datadog-build-reporter-0.1.1...datadog-build-reporter-0.1.2

### Changes
* [IMPROVEMENT] Added CHANGELOG.md
* [IMPROVEMENT] Added README.md

# 0.1.1 / 08-28-2015
### Details
Worked out kinks in the release process.

https://github.com/jenkinsci/datadog-plugin/compare/datadog-build-reporter-0.1.0...datadog-build-reporter-0.1.1

### Changes
* [BUGFIX] Javadoc: Fixed javadoc errors in class DatadogBuildListener.
* [BUGFIX] Javadoc: Fixed javadoc errors in method post.

# 0.1.0 / 08-28-2015
### Details
Initial Release

### Changes
* [FEATURE] Events: Started build
* [FEATURE] Events: Finished build
* [FEATURE] Metrics: Build duration (jenkins.job.duration)
* [FEATURE] Service Checks: Build status (jenkins.job.status)
