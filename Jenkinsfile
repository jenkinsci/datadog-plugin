def comonVersion = "2.356"
def recentLTS = "2.346.1"
def configurations = [
    [ platform: "linux", jdk: "11", jenkins: null ],
    // windows 
    [ platform: "windows", jdk: "11", jenkins: comonVersion ],
    // java 11 
    [ platform: "linux", jdk: "11", jenkins: comonVersion ],
    // windows
    [ platform: "windows", jdk: "11", jenkins: recentLTS ],
    // java 11
    [ platform: "linux", jdk: "11", jenkins: recentLTS ],
]

buildPlugin(configurations: configurations)