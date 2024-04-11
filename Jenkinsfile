def latestSupported = "2.453"
def recentLTS = "2.361.4"
def configurations = [
    [ platform: "linux", jdk: "11", jenkins: null ],
    [ platform: "windows", jdk: "11", jenkins: latestSupported ],
    [ platform: "linux", jdk: "11", jenkins: latestSupported ],
    [ platform: "windows", jdk: "11", jenkins: recentLTS ],
    [ platform: "linux", jdk: "11", jenkins: recentLTS ],
]

buildPlugin(configurations: configurations)