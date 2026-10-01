def minimumSupported = "2.541.3"
def commonsLangRemoved = "2.579"
def configurations = [
    [ platform: "linux", jdk: "17", jenkins: null ],
    [ platform: "windows", jdk: "17", jenkins: minimumSupported ],
    [ platform: "linux", jdk: "21", jenkins: commonsLangRemoved ],
    [ platform: "windows", jdk: "21", jenkins: commonsLangRemoved ],
]

buildPlugin(configurations: configurations)
