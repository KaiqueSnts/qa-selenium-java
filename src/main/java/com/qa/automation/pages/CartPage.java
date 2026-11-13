package com.qa.automation.pages;

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class CartPage extends BasePage {
    private static final By CART_ITEM_NAMES = By.className("inventory_item_name");
    private static final By CART_BADGE = By.className("shopping_cart_badge");

    public CartPage(WebDriver driver) {
        super(driver);
    }

    public List<String> getItemNames() {
        return getAllVisibleTexts(CART_ITEM_NAMES);
    }

    public void removeProduct(String productName) {
        click(removeButton(productName));
    }

    public boolean isEmpty() {
        waitUntilNoElements(CART_ITEM_NAMES);
        return driver.findElements(CART_ITEM_NAMES).isEmpty();
    }

    public int getCartItemCount() {
        return driver.findElements(CART_BADGE).stream()
                .map(WebElement::getText)
                .mapToInt(Integer::parseInt)
                .findFirst()
                .orElse(0);
    }

    private By removeButton(String productName) {
        String productId = productName.toLowerCase().replace(" ", "-");
        return By.cssSelector("[data-test='remove-" + productId + "']");
    }
}
