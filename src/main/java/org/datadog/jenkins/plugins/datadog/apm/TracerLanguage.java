package org.datadog.jenkins.plugins.datadog.apm;

public enum TracerLanguage {
    DOTNET(".NET"),
    JAVA("Java"),
    JAVASCRIPT("JS"),
    PYTHON("Python");

    private final String label;

    TracerLanguage(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
