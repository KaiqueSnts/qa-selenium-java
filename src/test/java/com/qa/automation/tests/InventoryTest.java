package com.qa.automation.tests;

import com.qa.automation.pages.CartPage;
import com.qa.automation.pages.InventoryPage;
import com.qa.automation.pages.LoginPage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTest extends BaseTest {
    private static final String PRODUCT_NAME = "Sauce Labs Backpack";

    @Test
    void deveCarregarProdutosNaPaginaProducts() {
        InventoryPage inventoryPage = openInventory();

        assertTrue(inventoryPage.isLoaded());
        assertTrue(inventoryPage.getProductNames().contains(PRODUCT_NAME),
                "A lista deve exibir o Sauce Labs Backpack");
    }

    @Test
    void deveAdicionarProdutoAoCarrinho() {
        InventoryPage inventoryPage = openInventory();
        inventoryPage.addProductToCart(PRODUCT_NAME);

        assertTrue(inventoryPage.isProductInCart(PRODUCT_NAME));
    }

    @Test
    void deveAtualizarContadorAoAdicionarProduto() {
        InventoryPage inventoryPage = openInventory();
        inventoryPage.addProductToCart(PRODUCT_NAME);

        assertEquals(1, inventoryPage.getCartItemCount());
    }

    @Test
    void deveExibirProdutoCorretoNoCarrinho() {
        InventoryPage inventoryPage = openInventory();
        inventoryPage.addProductToCart(PRODUCT_NAME);

        CartPage cartPage = inventoryPage.openCart();
        assertEquals(List.of(PRODUCT_NAME), cartPage.getItemNames());
    }

    @Test
    void deveRemoverProdutoEDeixarCarrinhoVazio() {
        InventoryPage inventoryPage = openInventory();
        inventoryPage.addProductToCart(PRODUCT_NAME);

        CartPage cartPage = inventoryPage.openCart();
        cartPage.removeProduct(PRODUCT_NAME);

        assertTrue(cartPage.isEmpty());
        assertEquals(0, cartPage.getCartItemCount());
    }

    private InventoryPage openInventory() {
        return new LoginPage(driver)
                .open()
                .login("standard_user", "secret_sauce");
    }
}
