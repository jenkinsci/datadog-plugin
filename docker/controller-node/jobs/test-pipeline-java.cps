pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                git url: 'git@github.com:DataDog/ciapp-test-resources.git',
                    credentialsId: 'github-ssh'
            }
        }

        stage('Build') {
            steps {
                sh '''
                cd java/gradle-junit4
                JAVA_OPTS= ./gradlew test
                '''
            }
        }
    }
}
