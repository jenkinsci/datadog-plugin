pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                sh '''
                echo test downstream
                '''
            }
        }
    }
}
