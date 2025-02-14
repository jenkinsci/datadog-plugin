pipeline {
    agent {
        label 'agent-go'
    }
    stages {
        stage('Checkout') {
            steps {
                git url: 'git@github.com:gin-gonic/gin.git',
                    credentialsId: 'github-ssh'
            }
        }

        stage('Build') {
            steps {
                sh '''
                go test
                '''
            }
        }
    }
}
