@Library("test-shared-lib@main") _

pipeline {
    agent any
    stages {
        stage('Hello') {
            steps {
                sayhello "Alice"
            }
        }
    }
}
