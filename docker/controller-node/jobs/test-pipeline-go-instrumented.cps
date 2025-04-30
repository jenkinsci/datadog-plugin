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

        datadog(testOptimization: [ enabled: true, serviceName: "jenkins-test-go", languages: ["GO"], additionalVariables: [:] ]) {
            node {
                stage('Build') {
                    steps {
                        sh '''
                        go test
                        '''
                    }
                }
            }
        }
    }
}
