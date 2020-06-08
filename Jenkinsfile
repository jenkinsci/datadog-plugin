#!groovy

def customConfigurations = [
    // Test with lowest compatible version
    [ platform: "linux", jdk: "8", jenkins: null ],
    [ platform: "windows", jdk: "8", jenkins: null ],
    // Test with common, "medium" version
    [ platform: "linux", jdk: "8", jenkins: "2.222.3" ],
    [ platform: "windows", jdk: "8", jenkins: "2.222.3", javaLevel: "8" ],
    // Test with new version
    [ platform: "linux", jdk: "8", jenkins: "2.238", javaLevel: "8" ],
    [ platform: "windows", jdk: "8", jenkins: "2.238", javaLevel: "8" ],
    //Test with java 11
    [ platform: "linux", jdk: "11", jenkins: "2.238", javaLevel: "8" ],
    [ platform: "windows", jdk: "11", jenkins: "2.238", javaLevel: "8" ],
    [ platform: "windows", jdk: "11", jenkins: "2.238", javaLevel: "11" ],
]

buildPlugin(configurations: buildPlugin.recommendedConfigurations() + customConfigurations)