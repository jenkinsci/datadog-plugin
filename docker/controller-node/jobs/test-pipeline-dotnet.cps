pipeline {
    agent {
        label 'agent-dotnet'
    }
    stages {
        stage('Checkout') {
            steps {
                git url: 'git@github.com:mbdavid/LiteDB.git',
                    credentialsId: 'github-ssh'
            }
        }

        stage('Build') {
            steps {
                sh '''
                dotnet test
                '''
            }
        }
    }
}
