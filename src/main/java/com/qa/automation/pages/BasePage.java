package com.qa.automation.pages;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
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

    protected void waitForElementCount(By locator, int expectedCount) {
        wait.until(webDriver -> webDriver.findElements(locator).size() == expectedCount);
    }

    protected void waitForText(By locator, String expectedText) {
        wait.until(webDriver -> {
            try {
                WebElement element = webDriver.findElement(locator);
                return element.isDisplayed() && expectedText.equals(element.getText());
            } catch (StaleElementReferenceException exception) {
                return false;
            }
        });
    }

    protected void click(By locator) {
        wait.until(webDriver -> {
            try {
                WebElement element = ExpectedConditions.elementToBeClickable(locator).apply(webDriver);
                if (element == null) {
                    return false;
                }
                element.click();
                return true;
            } catch (StaleElementReferenceException exception) {
                return false;
            }
        });
    }

    protected void type(By locator, String text) {
        wait.until(webDriver -> {
            try {
                WebElement element = ExpectedConditions.visibilityOfElementLocated(locator).apply(webDriver);
                if (element == null || !element.isEnabled()) {
                    return false;
                }
                element.clear();
                element.sendKeys(text);
                WebElement updatedElement = webDriver.findElement(locator);
                return updatedElement.isDisplayed() && text.equals(updatedElement.getDomProperty("value"));
            } catch (StaleElementReferenceException exception) {
                return false;
            }
        });
    }

    protected String getText(By locator) {
        return wait.until(webDriver -> {
            try {
                WebElement element = webDriver.findElement(locator);
                return element.isDisplayed() ? element.getText() : null;
            } catch (StaleElementReferenceException exception) {
                return null;
            }
        });
    }

    protected void waitUntil(Predicate<WebDriver> condition) {
        wait.until(condition::test);
    }

    protected int getOptionalCount(By locator) {
        return wait.until(webDriver -> {
            List<WebElement> elements = webDriver.findElements(locator);
            if (elements.isEmpty()) {
                return 0;
            }
            try {
                return Integer.parseInt(elements.get(0).getText());
            } catch (StaleElementReferenceException exception) {
                return null;
            }
        });
    }

    protected boolean isVisible(By locator) {
        try {
            return findVisible(locator).isDisplayed();
        } catch (org.openqa.selenium.TimeoutException exception) {
            return false;
        }
    }
}
