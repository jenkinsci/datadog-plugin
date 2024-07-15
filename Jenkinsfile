def latestSupported = "2.407"
def recentLTS = "2.361.4"
def configurations = [
    [ platform: "linux", jdk: "11", jenkins: null ],
    [ platform: "windows", jdk: "11", jenkins: latestSupported ],
    [ platform: "linux", jdk: "11", jenkins: latestSupported ],
    [ platform: "windows", jdk: "11", jenkins: recentLTS ],
    [ platform: "linux", jdk: "11", jenkins: recentLTS ],
]

pipeline {
    agent any
    environment {
        JAVA_TOOL_OPTIONS = '-javaagent:non-existing-file.jar'
    }
    stages {
        stage('Build Plugin') {
            steps {
                script {
                    buildPlugin(configurations: configurations)
                }
            }
        }
    }
}
