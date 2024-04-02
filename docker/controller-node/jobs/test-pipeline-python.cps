pipeline {
    agent {
        label 'agent-python'
    }
    stages {
        stage('Checkout') {
            steps {
                git url: 'git@github.com:encode/django-rest-framework.git',
                        credentialsId: 'github-ssh'
            }
        }

        stage('Build') {
            steps {
                sh '''
                python3 -m pip install --disable-pip-version-check "Django>=4.2,<5.0"
                python3 -m pip install --disable-pip-version-check -r requirements/requirements-optionals.txt -r requirements/requirements-testing.txt
                python3 ./runtests.py
                '''
            }
        }
    }
}
