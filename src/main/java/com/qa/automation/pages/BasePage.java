package com.qa.automation.pages;

import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Common browser interactions shared by page objects. */
public abstract class BasePage {
    protected final WebDriver driver;
    private final WebDriverWait wait;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    protected WebElement findVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected List<String> getAllVisibleTexts(By locator) {
        return wait.until(webDriver -> {
            try {
                List<WebElement> elements = webDriver.findElements(locator);
                if (elements.isEmpty() || elements.stream().anyMatch(element -> !element.isDisplayed())) {
                    return null;
                }
                return elements.stream().map(WebElement::getText).toList();
            } catch (StaleElementReferenceException exception) {
                return null;
            }
        });
    }

    protected void waitUntilNoElements(By locator) {
        wait.until(webDriver -> webDriver.findElements(locator).isEmpty());
    }

    protected void click(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    protected void type(By locator, String text) {
        WebElement element = findVisible(locator);
        element.clear();
        element.sendKeys(text);
    }

    protected String getText(By locator) {
        return findVisible(locator).getText();
    }

    protected boolean isVisible(By locator) {
        try {
            return findVisible(locator).isDisplayed();
        } catch (org.openqa.selenium.TimeoutException exception) {
            return false;
        }
    }
}
