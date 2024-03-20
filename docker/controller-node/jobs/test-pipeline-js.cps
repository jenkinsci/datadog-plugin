pipeline {
    agent {
        label 'agent-js'
    }
    stages {
        stage('Checkout') {
            steps {
                git url: 'git@github.com:expressjs/express.git',
                    credentialsId: 'github-ssh'
            }
        }

        stage('Build') {
            steps {
                sh '''
                npm install
                DD_TRACE_DEBUG=1 npm test
                '''
            }
        }
    }
}
