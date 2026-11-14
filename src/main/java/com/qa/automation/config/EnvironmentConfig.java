package com.qa.automation.config;

/** Runtime URLs and demo API credentials, overridable with environment variables. */
public class EnvironmentConfig {
    private final String webUrl = value("WEB_URL", "https://www.saucedemo.com");
    private final String apiUrl = value("API_URL", "https://restful-booker.herokuapp.com");
    private final String apiUsername = value("API_USERNAME", "admin");
    private final String apiPassword = value("API_PASSWORD", "password123");

    private static String value(String key, String defaultValue) {
        String configured = System.getenv(key);
        return configured == null || configured.isBlank() ? defaultValue : configured;
    }

    public String webUrl() { return webUrl; }
    public String apiUrl() { return apiUrl; }
    public String apiUsername() { return apiUsername; }
    public String apiPassword() { return apiPassword; }
}
