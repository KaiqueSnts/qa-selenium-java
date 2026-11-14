package com.qa.automation.tests;

import com.qa.automation.pages.CartPage;
import com.qa.automation.pages.InventoryPage;
import com.qa.automation.pages.LoginPage;
import com.qa.automation.tests.fixtures.TestProducts;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTest extends BaseTest {
    private static final String PRODUCT_NAME = TestProducts.BACKPACK;

    @Test
    @Tag("smoke")
    @Tag("regression")
    void deveCarregarProdutosNaPaginaProducts() {
        InventoryPage inventoryPage = openInventory();

        assertTrue(inventoryPage.isLoaded());
        assertTrue(inventoryPage.getProductNames().contains(PRODUCT_NAME),
                "A lista deve exibir o Sauce Labs Backpack");
    }

    @Test
    @Tag("regression")
    void deveAdicionarProdutoAoCarrinho() {
        InventoryPage inventoryPage = openInventory();
        inventoryPage.addProductToCart(PRODUCT_NAME);

        assertTrue(inventoryPage.isProductInCart(PRODUCT_NAME));
    }

    @Test
    @Tag("regression")
    void deveAtualizarContadorAoAdicionarProduto() {
        InventoryPage inventoryPage = openInventory();
        inventoryPage.addProductToCart(PRODUCT_NAME);

        inventoryPage.waitForCartItemCount(1);
        assertEquals(1, inventoryPage.getCartItemCount());
    }

    @Test
    @Tag("regression")
    void deveExibirProdutoCorretoNoCarrinho() {
        InventoryPage inventoryPage = openInventory();
        inventoryPage.addProductToCart(PRODUCT_NAME);

        CartPage cartPage = inventoryPage.openCart();
        assertEquals(List.of(PRODUCT_NAME), cartPage.getItemNames());
    }

    @Test
    @Tag("regression")
    void deveRemoverProdutoEDeixarCarrinhoVazio() {
        InventoryPage inventoryPage = openInventory();
        inventoryPage.addProductToCart(PRODUCT_NAME);
        assertTrue(inventoryPage.isProductInCart(PRODUCT_NAME),
                "O produto deve estar no carrinho antes da remoção");
        inventoryPage.waitForCartItemCount(1);
        assertEquals(1, inventoryPage.getCartItemCount());

        CartPage cartPage = inventoryPage.openCart();
        cartPage.removeProduct(PRODUCT_NAME);

        cartPage.waitForCartItemCount(0);
        assertTrue(cartPage.isEmpty());
        assertEquals(0, cartPage.getCartItemCount());
    }

    private InventoryPage openInventory() {
        return new LoginPage(driver)
                .open(config.webUrl())
                .login("standard_user", "secret_sauce");
    }
}
