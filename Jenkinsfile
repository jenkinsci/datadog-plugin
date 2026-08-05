def latestSupported = "2.576"
def recentLTS = "2.555.3"
def configurations = [
    [ platform: "linux", jdk: "25", jenkins: null ],
    [ platform: "windows", jdk: "25", jenkins: latestSupported ],
    [ platform: "linux", jdk: "25", jenkins: latestSupported ],
    [ platform: "windows", jdk: "21", jenkins: recentLTS ],
    [ platform: "linux", jdk: "21", jenkins: recentLTS ],
]

buildPlugin(configurations: configurations)
