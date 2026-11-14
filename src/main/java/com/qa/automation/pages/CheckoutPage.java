package com.qa.automation.pages;

import com.qa.automation.models.CheckoutInfo;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class CheckoutPage extends BasePage {
    private static final By FIRST_NAME = By.id("first-name");
    private static final By LAST_NAME = By.id("last-name");
    private static final By POSTAL_CODE = By.id("postal-code");
    private static final By CONTINUE_BUTTON = By.id("continue");
    private static final By FINISH_BUTTON = By.id("finish");
    private static final By ERROR_MESSAGE = By.cssSelector("[data-test='error']");
    private static final By COMPLETE_HEADER = By.className("complete-header");
    private static final String OVERVIEW_URL = "checkout-step-two.html";

    public CheckoutPage(WebDriver driver) {
        super(driver);
    }

    public void fillInformation(CheckoutInfo info) {
        type(FIRST_NAME, info.firstName());
        type(LAST_NAME, info.lastName());
        type(POSTAL_CODE, info.postalCode());
    }

    public void continueCheckout() {
        click(CONTINUE_BUTTON);
        waitUntil(webDriver -> webDriver.getCurrentUrl().contains(OVERVIEW_URL)
                || isErrorMessageVisible(webDriver));
    }

    public void finish() {
        click(FINISH_BUTTON);
        findVisible(COMPLETE_HEADER);
    }

    public String getErrorMessage() {
        return getText(ERROR_MESSAGE);
    }

    public String getCompleteHeader() {
        return getText(COMPLETE_HEADER);
    }

    private boolean isErrorMessageVisible(WebDriver webDriver) {
        try {
            for (WebElement element : webDriver.findElements(ERROR_MESSAGE)) {
                if (element.isDisplayed()) {
                    return true;
                }
            }
            return false;
        } catch (StaleElementReferenceException exception) {
            return false;
        }
    }
}
