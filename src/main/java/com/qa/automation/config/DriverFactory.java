package com.qa.automation.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/** Creates isolated Chrome sessions; Selenium Manager resolves the matching driver. */
public final class DriverFactory {
    private static final Map<WebDriver, Path> PROFILES = new ConcurrentHashMap<>();

    private DriverFactory() { }

    public static WebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();

        options.addArguments("--window-size=1440,1000");
        options.addArguments("--disable-features=PasswordLeakDetection");
        options.addArguments("--disable-save-password-bubble");

        Path userDataDir;
        try {
            userDataDir = Files.createTempDirectory("qa-chrome-profile-");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create temporary Chrome profile", exception);
        }

        options.addArguments("--user-data-dir=" + userDataDir.toAbsolutePath());

        options.setExperimentalOption("prefs", Map.of(
                "credentials_enable_service", false,
                "profile.password_manager_enabled", false,
                "profile.password_manager_leak_detection", false
        ));

        if (Boolean.parseBoolean(System.getenv().getOrDefault("HEADLESS", "false"))) {
            options.addArguments("--headless=new");
        }

        try {
            WebDriver driver = new ChromeDriver(options);
            PROFILES.put(driver, userDataDir);
            return driver;
        } catch (RuntimeException exception) {
            deleteProfileDirectory(userDataDir);
            throw exception;
        }
    }

    public static void quitDriver(WebDriver driver) {
        Path userDataDir = PROFILES.remove(driver);

        try {
            driver.quit();
        } finally {
            if (userDataDir != null) {
                deleteProfileDirectory(userDataDir);
            }
        }
    }

    private static void deleteProfileDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            System.err.println("Could not completely remove Chrome profile: "
                    + directory + " - " + exception.getMessage());
        }
    }
}
