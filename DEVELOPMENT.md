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

To spin up a development environment for the *jenkins-datadog* plugin repository. The requirements are:

* [Java 1.8](https://www.java.com/en/download/)
* [Docker](https://docs.docker.com/get-started/) & [docker-compose](https://docs.docker.com/compose/install/)
* [A clone/fork of this repository](https://help.github.com/en/articles/fork-a-repo)


1. To get started, save the following `docker-compose.yaml` file in your working directory locally:

    ```
    version: "3.7"
    services:
      jenkins:
        image: jenkins/jenkins:lts
        ports:
          - 8080:8080
        volumes:
          - $JENKINS_PLUGIN/target/:/var/jenkins_home/plugins
    ## Uncomment environment variables based on your needs. Everything can be configured in jenkins /configure page as well. 
    #   environment:
    #      - DATADOG_JENKINS_PLUGIN_REPORT_WITH=DSD
    #      - DATADOG_JENKINS_PLUGIN_COLLECT_BUILD_LOGS=false
    ## Set `DATADOG_JENKINS_PLUGIN_TARGET_HOST` to `dogstatsd` or `datadog` based on the container you wish to use.
    #      - DATADOG_JENKINS_PLUGIN_TARGET_HOST=dogstatsd
    #      - DATADOG_JENKINS_PLUGIN_TARGET_LOG_COLLECTION_PORT=10518
    #      - DATADOG_JENKINS_PLUGIN_TARGET_API_KEY=$JENKINS_PLUGIN_DATADOG_API_KEY
      
    ## Uncomment the section below to use the standalone DogStatsD server to send metrics to Datadog
    #  dogstatsd:
    #    image: datadog/dogstatsd:latest
    #    environment:
    #      - DD_API_KEY=$JENKINS_PLUGIN_DATADOG_API_KEY
    #    ports:
    #      - 8125:8125
    
    ## Uncomment the section below to use the whole Datadog Agent to send metrics (and logs) to Datadog. 
    ## Note that it contains a DogStatsD server as well.
    #  datadog:
    #    image: datadog/agent:latest
    #    environment:
    #      - DD_API_KEY=$JENKINS_PLUGIN_DATADOG_API_KEY
    #      - DD_LOGS_ENABLED=true
    #      - DD_DOGSTATSD_NON_LOCAL_TRAFFIC=true 
    #     ports:
    #       - 8125:8125
    #       - 10518:10518
    #    volumes:
    #      - /var/run/docker.sock:/var/run/docker.sock:ro
    #      - /proc/:/host/proc/:ro
    #      - /sys/fs/cgroup/:/host/sys/fs/cgroup:ro
    #      - $JENKINS_PLUGIN/conf.yaml:/etc/datadog-agent/conf.d/jenkins.d/conf.yaml   
               
    ```
1. If you wish to submit log using the Datadog Agent, you will have to configure the Datadog Agent properly by creating a `conf.yaml` file with the following content.

    ```
    logs:
      - type: tcp
        port: 10518
        service: "jenkins"
        source: "jenkins"
    ```
1. Set the `JENKINS_PLUGIN` environment variable to point to the directory where this repository is cloned/forked.
1. Set the `JENKINS_PLUGIN_DATADOG_API_KEY` environment variable with your api key.
1. Run `docker-compose -f <DOCKER_COMPOSE_FILE_PATH> up`.
    - NOTE: This spins up the Jenkins docker image and auto mount the target folder of this repository (the location where the binary is built)
    - NOTE: To see code updates, after re building the provider with `mvn clean package` on your local machine, run `docker-compose down` and spin this up again.
1. Check your terminal and look for the admin password:
    ```
    jenkins_1    | *************************************************************
    jenkins_1    | *************************************************************
    jenkins_1    | *************************************************************
    jenkins_1    |
    jenkins_1    | Jenkins initial setup is required. An admin user has been created and a password generated.
    jenkins_1    | Please use the following password to proceed to installation:
    jenkins_1    |
    jenkins_1    | <JENKINS_ADMIN_PASSWORD>
    jenkins_1    |
    jenkins_1    | This may also be found at: /var/jenkins_home/secrets/initialAdminPassword
    jenkins_1    |
    jenkins_1    | *************************************************************
    jenkins_1    | *************************************************************
    jenkins_1    | *************************************************************
    ``` 

1. Access your Jenkins instance http://localhost:8080
1. Enter the administrator password in the Getting Started form.
1. On the next page, click on the "Select plugins to be installed" unless you want to install all suggested plugins. 
1. Select desired plugins depending on your needs. You can always add plugins later.
1. Create a user so that you don't have to use the admin credential again (optional).
1. Continue until the end of the setup process and log back in.
1. Go to http://localhost:8080/configure to configure the "Datadog Plugin", set your `API Key`.
  - Click on the "Test Key" to make sure your key is valid.
  - You can set your machine `hostname`.
  - You can set Global Tag. For example `.*, owner:$1, release_env:$2, optional:Tag3`.
  
#### Manual Testing without an Agent

Alternatively, you can manually test the plugin by running the command `mvn hpi:run`, which will spin up a local development environment without the agent. This allows you to test using the HTTP client without needing docker. See the [jenkins documentation](https://jenkinsci.github.io/maven-hpi-plugin/run-mojo.html) for more details and options.

### Create your first job

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

## Continuous Integration

Every commit to the repository triggers the [Jenkins Org CI pipeline](https://jenkins.io/doc/developer/publishing/continuous-integration/) defined in the `Jenkinsfile` at the root folder of the source code.

## Troubleshooting

### Header is too large

When accessing your jenkins instance, you may run into the following warning
```
WARNING o.eclipse.jetty.http.HttpParser#parseFields: Header is too large 8193>8192
```
In this case, use your browser in incognito mode.