FROM jenkins/jenkins:latest

USER root

RUN apt-get -y update
RUN apt-get -y upgrade
RUN apt-get -y install curl wget less vim ssh gettext-base

COPY add-github-to-known-hosts.sh /var/jenkins_home/add-github-to-known-hosts.sh
RUN chmod 755 /var/jenkins_home/add-github-to-known-hosts.sh

USER jenkins

# needed for the script that creates credentials
ARG JENKINS_PLUGIN_DATADOG_API_KEY
ENV JENKINS_PLUGIN_DATADOG_API_KEY=$JENKINS_PLUGIN_DATADOG_API_KEY

# needed for CasC YAML
ARG JENKINS_PLUGIN_DATADOG_CI_INSTANCE_NAME
ENV JENKINS_PLUGIN_DATADOG_CI_INSTANCE_NAME=$JENKINS_PLUGIN_DATADOG_CI_INSTANCE_NAME

# to update the list of dependencies run `mvn hpi:list-plugin-dependencies -DoutputFile=/tmp/deps.txt && cat /tmp/deps.txt | cut -d: -f2 > docker/controller-node/plugin-dependencies.txt && rm /tmp/deps.txt`
COPY plugin-dependencies.txt /var/jenkins_home/plugin-dependencies.txt
RUN jenkins-plugin-cli --latest-specified --plugin-file /var/jenkins_home/plugin-dependencies.txt

# this is not a dependency of the Datadog plugin, it is just needed to test upstream/downstream pipeline linking
RUN jenkins-plugin-cli --latest-specified --plugins pipeline-build-step

COPY jobs /var/jenkins_home/sample-jobs

COPY 10-create-admin-user.groovy /usr/share/jenkins/ref/init.groovy.d/10-create-admin-user.groovy
COPY 20-create-agent-nodes.groovy /usr/share/jenkins/ref/init.groovy.d/20-create-agent-nodes.groovy
COPY 30-create-github-credentials.groovy /usr/share/jenkins/ref/init.groovy.d/30-create-github-credentials.groovy
COPY 40-create-datadog-key_credentials.groovy /usr/share/jenkins/ref/init.groovy.d/40-create-datadog-key_credentials.groovy
COPY 50-create-jobs.groovy /usr/share/jenkins/ref/init.groovy.d/50-create-jobs.groovy
COPY 51-create-multi-branch-pipeline.groovy /usr/share/jenkins/ref/init.groovy.d/51-create-multi-branch-pipeline.groovy
COPY 60-add-script-signature-approvals.groovy /usr/share/jenkins/ref/init.groovy.d/60-add-script-signature-approvals.groovy
COPY 70-configure-shared-library.groovy /usr/share/jenkins/ref/init.groovy.d/70-configure-shared-library.groovy

RUN /var/jenkins_home/add-github-to-known-hosts.sh

# prepare configuration as code (point CASC_JENKINS_CONFIG to this folder to use CasC)
COPY jenkins-casc.yaml /tmp/jenkins-casc.yaml
RUN mkdir -p /var/jenkins_home/casc
RUN cat /tmp/jenkins-casc.yaml | envsubst > /var/jenkins_home/casc/jenkins-casc.yaml

RUN mkdir -p /var/jenkins_home/shared
RUN mkdir -p /var/jenkins_home/plugins
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5055"
