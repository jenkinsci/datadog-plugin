package org.datadog.jenkins.plugins.datadog.configuration.api.intake;

public enum DatadogSite {
    US1(
            "datadoghq.com",
            "https://api.datadoghq.com/api/",
            "https://http-intake.logs.datadoghq.com/v1/input/",
            "https://webhook-intake.datadoghq.com/api/v2/webhook/"),
    US3(
            "us3.datadoghq.com",
            "https://api.us3.datadoghq.com/api/",
            "https://http-intake.logs.us3.datadoghq.com/v1/input/",
            "https://webhook-intake.us3.datadoghq.com/api/v2/webhook/"),
    US5(
            "us5.datadoghq.com",
            "https://api.us5.datadoghq.com/api/",
            "https://http-intake.logs.us5.datadoghq.com/v1/input/",
            "https://webhook-intake.us5.datadoghq.com/api/v2/webhook/"),
    US1_FED(
            "ddog-gov.com",
            "https://api.dd-gov.com/api/",
            "https://http-intake.logs.dd-gov.com/v1/input/",
            "https://webhook-intake.dd-gov.com/api/v2/webhook/"),
    EU1(
            "datadoghq.eu",
            "https://api.datadoghq.eu/api/",
            "https://http-intake.logs.datadoghq.eu/v1/input/",
            "https://webhook-intake.datadoghq.eu/api/v2/webhook/"),
    AP1(
            "ap1.datadoghq.com",
            "https://api.ap1.datadoghq.com/api/",
            "https://http-intake.logs.ap1.datadoghq.com/v1/input/",
            "https://webhook-intake.ap1.datadoghq.com/api/v2/webhook/");

    private final String siteName;
    private final String apiUrl;
    private final String logsUrl;
    private final String webhooksUrl;

    DatadogSite(String siteName, String apiUrl, String logsUrl, String webhooksUrl) {
        this.siteName = siteName;
        this.apiUrl = apiUrl;
        this.logsUrl = logsUrl;
        this.webhooksUrl = webhooksUrl;
    }

    public String getSiteName() {
        return siteName;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getLogsUrl() {
        return logsUrl;
    }

    public String getWebhooksUrl() {
        return webhooksUrl;
    }
}
