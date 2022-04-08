def recentLTS = "2.332.2"
def oldestVersion = "2.303.3"
def configurations = [
    [ platform: "linux", jdk: "8", jenkins: null ],
    // windows 
    [ platform: "windows", jdk: "8", jenkins: recentLTS ],
    // java 11 
    [ platform: "linux", jdk: "11", jenkins: recentLTS ],
    // windows
    [ platform: "windows", jdk: "8", jenkins: oldestVersion ],
    // java 11
    [ platform: "linux", jdk: "11", jenkins: oldestVersion ],
]

buildPlugin(configurations: configurations)