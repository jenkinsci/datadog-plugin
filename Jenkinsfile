def latestSupported = "2.453"
def recentLTS = "2.361.4"
def configurations = [
    [ platform: "linux", jdk: "11", jenkins: null ],
    // windows 
    [ platform: "windows", jdk: "17", jenkins: latestSupported ],
    // java 11 
    [ platform: "linux", jdk: "17", jenkins: latestSupported ],
    // windows
    [ platform: "windows", jdk: "11", jenkins: recentLTS ],
    // java 11
    [ platform: "linux", jdk: "11", jenkins: recentLTS ],
]

buildPlugin(configurations: configurations)