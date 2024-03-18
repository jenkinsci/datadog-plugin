# Development

## General

We love pull requests. Here's a quick guide.

Fork, then clone the repo:

    git clone git@github.com:jenkinsci/datadog-plugin.git

Make sure the tests pass:

    mvn test

Make your change. Add tests for your change. Make the tests pass again.
It is strongly recommended to perform manual testing as well, see section below.


Push to your fork and [submit a pull request][pr].

[pr]: https://github.com/your-username/datadog-plugin/compare/jenkinsci:master...master

At this point you're waiting on us. We may suggest some changes, improvements or alternatives.

## Manual Testing

### Setup

The docker environment described below should only be used for development and testing purposes. __It is not production-ready.__

To spin up a development environment for the *jenkins-datadog* plugin repository. The requirements are:

* [Java 1.8](https://www.java.com/en/download/)
* [Docker](https://docs.docker.com/get-started/) & [docker-compose](https://docs.docker.com/compose/install/)
* [A clone/fork of this repository](https://help.github.com/en/articles/fork-a-repo)

1. Set the `JENKINS_PLUGIN` environment variable to point to the directory where this repository is cloned/forked.
1. Set the `JENKINS_PLUGIN_DATADOG_API_KEY` environment variable with your api key.
1. Optionally set the `GITHUB_SSH_KEY` and `GITHUB_SSH_KEY_PASSPHRASE` environment variables with the key and passphrase that can be used to access GitHub. This allows to automatically create GitHub credentials in Jenkins.   
1. Run `mvn clean package -DskipTests` and `docker-compose -p datadog-jenkins-plugin -f docker/docker-compose.yaml up` from the directory where this repository is cloned/forked (if the `docker-compose` command fails because with a `path ... not found` error, try updating it to the latest version).
    - NOTE: This spins up the Jenkins docker image and auto mounts the target folder of this repository (the location where the binary is built).
    - NOTE: To see code updates after re-running the maven build on your local machine, run `docker-compose -p datadog-jenkins-plugin -f docker/docker-compose.yaml down` and spin it up again.
1. Access your Jenkins instance http://localhost:8080 with the admin credentials `admin`/`local-jenkins-instance-admin-password`.
1. Go to http://localhost:8080/configure to configure the "Datadog Plugin":
  - Click on the "Test Key" to make sure that the key you set using `JENKINS_PLUGIN_DATADOG_API_KEY` is valid.
  - You can set your machine `hostname`.
  - You can set Global Tag. For example `.*, owner:$1, release_env:$2, optional:Tag3`.

Jenkins controller container exposes port 5055 for remote debugging via JDWP. 

#### Manual Testing without an Agent

Alternatively, you can manually test the plugin by running the command `mvn hpi:run`, which will spin up a local development environment without the agent. This allows you to test using the HTTP client without needing docker. See the [jenkins documentation](https://jenkinsci.github.io/maven-hpi-plugin/run-mojo.html) for more details and options.

### Create your first job

If you use the `docker-compose.yaml` available in this repository, some sample jobs and pipelines will be provisioned by default.
You can also create a new job to test the plugin.

1. On jenkins Home page, click on "Create a new Job"
1. Give it a name and select "freestyle project".
1. Then add a build step (execute Shell):
    ```
    #!/bin/sh

    echo "Executing my job script"
    sleep 5s
    ```

### Create Logger

1. Go to http://localhost:8080/log/
1. Give a name to your logger - For example `datadog`
1. Add entries for all `org.datadog.jenkins.plugins.datadog.*` packages with log Level `ALL`.
1. If you now run a job and go back to http://localhost:8080/log/datadog/, you should see your logs

### Add a Jenkins Agent

If you use the `docker-compose.yaml` available in this repository, a few agent nodes will be provisioned by default. 
The agents have different runtimes installed (Python, JS, .NET) as indicated in their names.
If you do not need the agents, you can comment out the `jenkins-agent-...` services in the `docker-compose.yaml` file.

If you want to set up your own agent, you can follow these steps:

1. Go to `Configure Jenkins > Manage Nodes > New Node`. Enter a node name and select `Permanent Agent`.
2. Select `Launch Method via Java Web Start` for `Launch Method` and save the node.
3. Go to `http://localhost:8080/jenkins/computer/{node_name}` and press the `launch button`. Open the `.jnlp` file and copy the key.
4. Start the agent with:
   ```
   docker run -d --init jenkins/inbound-agent -url http://host.docker.internal:8080/jenkins <KEY> <NODE_NAME>
   ```

#### Jobs failing with Jenkins Agent

Due to backwards compatability of the plugin, the Jenkins version defined in `pom.xml` might have dependency issues that can cause jobs running on an agent to fail. To fix, find the minimum version required by dependencies and modify the version in [`pom.xml`](https://github.com/jenkinsci/datadog-plugin/blob/master/pom.xml#L23):

```
...
<properties>
    <java.level>8</java.level>
    <jenkins.version>{VERSION}</jenkins.version>
    <hpi.compatibleSinceVersion>1.0.0</hpi.compatibleSinceVersion>
  </properties>
...
```

## Continuous Integration

Every commit to the repository triggers the [Jenkins Org CI pipeline](https://jenkins.io/doc/developer/publishing/continuous-integration/) defined in the `Jenkinsfile` at the root folder of the source code.

## Troubleshooting

### Header is too large

When accessing your jenkins instance, you may run into the following warning
```
WARNING o.eclipse.jetty.http.HttpParser#parseFields: Header is too large 8193>8192
```
In this case, use your browser in incognito mode (or clear the cookies for your local jenkins instance).

### Unsupported class file major version 57

If pipeline jobs fail with the error `java.lang.IllegalArgumentException: Unsupported class file major version 57`, then double-check the version of Java running the server is `1.8`.  Note that `mvn` can find a different version that what may be in your path, you can verify via `mvn --version`.
