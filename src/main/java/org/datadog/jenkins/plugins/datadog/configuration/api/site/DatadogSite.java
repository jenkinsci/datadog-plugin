package org.datadog.jenkins.plugins.datadog.configuration.api.site;

public enum DatadogSite {
    US1("https://api.datadoghq.com/api/", "https://http-intake.logs.datadoghq.com/v1/input/", "https://webhook-intake.datadoghq.com/api/v2/webhook/");

    private final String apiURL;
    private final String logIntakeURL;
    private final String webhookIntakeURL;

    DatadogSite(String apiURL, String logIntakeURL, String webhookIntakeURL) {
        this.apiURL = apiURL;
        this.logIntakeURL = logIntakeURL;
        this.webhookIntakeURL = webhookIntakeURL;
    }

    public String getApiURL() {
        return apiURL;
    }

    public String getLogIntakeURL() {
        return logIntakeURL;
    }

    public String getWebhookIntakeURL() {
        return webhookIntakeURL;
    }
}
