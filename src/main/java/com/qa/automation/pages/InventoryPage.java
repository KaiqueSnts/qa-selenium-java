package com.qa.automation.pages;

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class InventoryPage extends BasePage {
    private static final By PAGE_TITLE = By.className("title");
    private static final By PRODUCT_NAMES = By.className("inventory_item_name");
    private static final By CART_BADGE = By.className("shopping_cart_badge");
    private static final By CART_LINK = By.className("shopping_cart_link");
    private static final By MENU_BUTTON = By.id("react-burger-menu-btn");
    private static final By LOGOUT_LINK = By.id("logout_sidebar_link");

    public InventoryPage(WebDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return driver.getCurrentUrl().contains("inventory.html")
                && "Products".equals(getText(PAGE_TITLE));
    }

    public InventoryPage open(String baseUrl) {
        driver.get(baseUrl.replaceAll("/$", "") + "/inventory.html");
        return this;
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

    public void removeProductFromCart(String productName) {
        click(productButton("remove", productName));
    }

    public int getCartItemCount() {
        return getOptionalCount(CART_BADGE);
    }

    public void waitForCartItemCount(int expectedCount) {
        if (expectedCount == 0) {
            waitUntilNoElements(CART_BADGE);
        } else {
            waitForText(CART_BADGE, Integer.toString(expectedCount));
        }
    }

    public boolean isProductInCart(String productName) {
        return isVisible(productButton("remove", productName));
    }

    public CartPage openCart() {
        click(CART_LINK);
        return new CartPage(driver);
    }

    public void logout() {
        click(MENU_BUTTON);
        click(LOGOUT_LINK);
    }

    private By productButton(String action, String productName) {
        String productId = productName.toLowerCase().replace(" ", "-");
        return By.cssSelector("[data-test='" + action + "-" + productId + "']");
    }
}
