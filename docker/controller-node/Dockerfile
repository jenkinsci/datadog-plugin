FROM jenkins/jenkins:latest

USER root

RUN apt-get -y update
RUN apt-get -y upgrade
RUN apt-get -y install curl wget less vim ssh

COPY add-github-to-known-hosts.sh /var/jenkins_home/add-github-to-known-hosts.sh
RUN chmod 755 /var/jenkins_home/add-github-to-known-hosts.sh

USER jenkins

# to update the list of dependencies run `mvn hpi:list-plugin-dependencies -DoutputFile=/tmp/deps.txt && cat /tmp/deps.txt | cut -d: -f2 > docker/controller-node/plugin-dependencies.txt && rm /tmp/deps.txt`
COPY plugin-dependencies.txt /var/jenkins_home/plugin-dependencies.txt
RUN jenkins-plugin-cli --latest-specified --plugin-file /var/jenkins_home/plugin-dependencies.txt

# this is not a dependency of the Datadog plugin, it is just needed to test upstream/downstream pipeline linking
RUN jenkins-plugin-cli --latest-specified --plugins pipeline-build-step

COPY jobs /var/jenkins_home/sample-jobs

COPY 10-create-admin-user.groovy /usr/share/jenkins/ref/init.groovy.d/10-create-admin-user.groovy
COPY 20-create-agent-nodes.groovy /usr/share/jenkins/ref/init.groovy.d/20-create-agent-nodes.groovy
COPY 30-create-github-credentials.groovy /usr/share/jenkins/ref/init.groovy.d/30-create-github-credentials.groovy
COPY 40-create-jobs.groovy /usr/share/jenkins/ref/init.groovy.d/40-create-jobs.groovy

RUN /var/jenkins_home/add-github-to-known-hosts.sh

RUN mkdir -p /var/jenkins_home/shared
RUN mkdir -p /var/jenkins_home/plugins
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5055"
