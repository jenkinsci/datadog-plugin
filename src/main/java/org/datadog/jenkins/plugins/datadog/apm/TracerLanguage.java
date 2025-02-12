package org.datadog.jenkins.plugins.datadog.apm;

public enum TracerLanguage {
    DOTNET(".NET"),
    GO("Golang"),
    JAVA("Java"),
    JAVASCRIPT("JS"),
    PYTHON("Python"),
    RUBY("Ruby");

    private final String label;

    TracerLanguage(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
