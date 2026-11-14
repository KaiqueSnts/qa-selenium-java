package com.qa.automation.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/** Captures a browser screenshot after a failing test, before BaseTest closes Chrome. */
class FailureScreenshotExtension implements AfterTestExecutionCallback {
    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }

        context.getTestInstance()
                .filter(BaseTest.class::isInstance)
                .map(BaseTest.class::cast)
                .map(BaseTest::activeDriver)
                .ifPresent(driver -> capture(driver, context));
    }

    private void capture(WebDriver driver, ExtensionContext context) {
        if (!(driver instanceof TakesScreenshot screenshotDriver)) {
            return;
        }
        String testName = context.getRequiredTestClass().getSimpleName()
                + "_" + context.getRequiredTestMethod().getName();
        testName = testName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path destination = Path.of("target", "screenshots", testName + ".png");
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(screenshotDriver.getScreenshotAs(OutputType.FILE).toPath(), destination,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // Screenshot capture is diagnostic and must not mask the original test failure.
        }
    }
}
