package com.qa.automation.tests;

import com.qa.automation.config.DriverFactory;
import com.qa.automation.config.EnvironmentConfig;
import com.qa.automation.pages.InventoryPage;
import com.qa.automation.pages.LoginPage;
import com.qa.automation.tests.fixtures.DemoUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.WebDriver;

@Tag("web")
public abstract class BaseTest {
    @RegisterExtension
    static final FailureScreenshotExtension FAILURE_SCREENSHOT = new FailureScreenshotExtension();

    protected WebDriver driver;
    protected EnvironmentConfig config;

    @BeforeEach
    protected void setUp() {
        config = new EnvironmentConfig();
        driver = DriverFactory.createChromeDriver();
    }

    @AfterEach
    protected void tearDown() {
        if (driver != null) {
            DriverFactory.quitDriver(driver);
        }
    }

    protected InventoryPage loginAsStandardUser() {
        new LoginPage(driver).open(config.webUrl())
                .login(DemoUsers.STANDARD.username(), DemoUsers.STANDARD.password());
        return new InventoryPage(driver);
    }

    WebDriver activeDriver() {
        return driver;
    }
}
