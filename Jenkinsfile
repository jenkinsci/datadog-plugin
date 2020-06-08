#!groovy

def customConfigurations = [
    // Test with lowest compatible version
    [ platform: "linux", jdk: "8", jenkins: null ],
    [ platform: "windows", jdk: "8", jenkins: null ],
    //Test with java 11
    [ platform: "linux", jdk: "11", jenkins: "2.164.1", javaLevel: "8" ],
    [ platform: "windows", jdk: "11", jenkins: "2.164.1", javaLevel: "8" ],
]

buildPlugin(configurations: buildPlugin.recommendedConfigurations() + customConfigurations)