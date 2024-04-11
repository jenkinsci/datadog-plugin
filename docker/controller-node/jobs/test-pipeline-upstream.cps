pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                sh '''
                echo test
                '''
                build job: 'test-pipeline-downstream'
            }
        }
    }
}
