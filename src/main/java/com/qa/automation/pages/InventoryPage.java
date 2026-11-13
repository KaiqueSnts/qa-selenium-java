package com.qa.automation.pages;

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class InventoryPage extends BasePage {
    private static final By PAGE_TITLE = By.className("title");
    private static final By PRODUCT_NAMES = By.className("inventory_item_name");
    private static final By CART_BADGE = By.className("shopping_cart_badge");
    private static final By CART_LINK = By.className("shopping_cart_link");

    public InventoryPage(WebDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return driver.getCurrentUrl().contains("inventory.html")
                && "Products".equals(getText(PAGE_TITLE));
    }

    public String getTitle() {
        return getText(PAGE_TITLE);
    }

    public List<String> getProductNames() {
        return getAllVisibleTexts(PRODUCT_NAMES);
    }

    public void addProductToCart(String productName) {
        click(productButton("add-to-cart", productName));
    }

    public int getCartItemCount() {
        return driver.findElements(CART_BADGE).stream()
                .map(WebElement::getText)
                .mapToInt(Integer::parseInt)
                .findFirst()
                .orElse(0);
    }

    public boolean isProductInCart(String productName) {
        return isVisible(productButton("remove", productName));
    }

    public CartPage openCart() {
        click(CART_LINK);
        return new CartPage(driver);
    }

    private By productButton(String action, String productName) {
        String productId = productName.toLowerCase().replace(" ", "-");
        return By.cssSelector("[data-test='" + action + "-" + productId + "']");
    }
}
