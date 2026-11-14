package com.qa.automation.pages;

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class CartPage extends BasePage {
    private static final By CART_ITEM_NAMES = By.className("inventory_item_name");
    private static final By CART_ITEMS = By.className("cart_item");
    private static final By CART_LIST = By.className("cart_list");
    private static final By CART_BADGE = By.className("shopping_cart_badge");
    private static final By CHECKOUT_BUTTON = By.id("checkout");

    public CartPage(WebDriver driver) {
        super(driver);
    }

    public List<String> getItemNames() {
        return getAllVisibleTexts(CART_ITEM_NAMES);
    }

    public int getItemCount() {
        findVisible(CART_LIST);
        return driver.findElements(CART_ITEMS).size();
    }

    public void waitForItemCount(int expectedCount) {
        waitForElementCount(CART_ITEMS, expectedCount);
    }

    public void waitForCartItemCount(int expectedCount) {
        if (expectedCount == 0) {
            waitUntilNoElements(CART_BADGE);
        } else {
            waitForText(CART_BADGE, Integer.toString(expectedCount));
        }
    }

    public boolean isEmpty() {
        waitUntilNoElements(CART_ITEMS);
        return driver.findElements(CART_ITEMS).isEmpty();
    }

    public int getCartItemCount() {
        return getOptionalCount(CART_BADGE);
    }

    public void removeProduct(String productName) {
        click(removeButton(productName));
    }

    public CheckoutPage checkout() {
        click(CHECKOUT_BUTTON);
        return new CheckoutPage(driver);
    }

    private By removeButton(String productName) {
        String productId = productName.toLowerCase().replace(" ", "-");
        return By.cssSelector("[data-test='remove-" + productId + "']");
    }
}
