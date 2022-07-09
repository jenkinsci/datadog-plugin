def comonVersion = "2.357"
def recentLTS = "2.346.1"
def configurations = [
    [ platform: "linux", jdk: "8", jenkins: null ],
    // windows 
    [ platform: "windows", jdk: "8", jenkins: comonVersion ],
    // java 11 
    [ platform: "linux", jdk: "11", jenkins: comonVersion ],
    // windows
    [ platform: "windows", jdk: "8", jenkins: recentLTS ],
    // java 11
    [ platform: "linux", jdk: "11", jenkins: recentLTS ],
]

buildPlugin(configurations: configurations)