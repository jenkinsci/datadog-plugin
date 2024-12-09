pipeline {
    agent {
        label 'agent-ruby'
    }
    stages {
        stage('Checkout') {
            steps {
                git url: 'git@github.com:freeCodeCamp/devdocs.git',
                        credentialsId: 'github-ssh'
            }
        }

        stage('Build') {
            steps {
                sh '''
                bundle install
                bundle exec thor test:all
                '''
            }
        }
    }
}
