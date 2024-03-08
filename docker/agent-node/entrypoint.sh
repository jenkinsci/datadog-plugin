#!/bin/bash

JNLP_SECRET_PATH="/shared/$JENKINS_AGENT_NAME.secret"

wait_for_jenkins_master() {
    echo "Waiting for Jenkins master at ${JENKINS_URL} to be available..."
    while ! curl --output /dev/null --silent --head --fail "$JENKINS_URL"; do
        printf '.'
        sleep 2
    done

    echo "Waiting for Jenkins agent secret to be available..."
    while true; do
        if [ -f "$JNLP_SECRET_PATH" ]; then
            break
        else
            printf '.'
            sleep 2
        fi
    done

    echo "Jenkins master is up."
}

wait_for_jenkins_master

curl -sO "${JENKINS_URL}/jnlpJars/agent.jar"

exec java -jar agent.jar \
  -url "${JENKINS_URL}" \
  -name "${JENKINS_AGENT_NAME}" \
  -secret "$(cat $JNLP_SECRET_PATH)" \
  -workDir "${JENKINS_AGENT_WORKDIR}" "$@"
