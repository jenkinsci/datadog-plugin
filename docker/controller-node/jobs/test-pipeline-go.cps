pipeline {
    agent {
        label 'agent-go'
    }
    stages {
        stage('Checkout') {
            steps {
                git url: 'git@github.com:gin-gonic/gin.git',
                    credentialsId: 'github-ssh', branch: 'main'
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
