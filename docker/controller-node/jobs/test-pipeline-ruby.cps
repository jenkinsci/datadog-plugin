pipeline {
    agent {
        label 'agent-ruby'
    }
    stages {
        stage('Checkout') {
            steps {
                git url: 'git@github.com:freeCodeCamp/devdocs.git',
                        credentialsId: 'github-ssh', branch: 'main'
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
