package com.qa.automation.tests.web;

import com.qa.automation.pages.InventoryPage;
import com.qa.automation.tests.BaseTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSetupTest extends BaseTest {
    @Test
    @Tag("smoke")
    @Tag("regression")
    void autenticaViaUiEAbreCatalogo() {
        InventoryPage inventory = loginAsStandardUser();
        assertTrue(inventory.isLoaded());
    }
}
