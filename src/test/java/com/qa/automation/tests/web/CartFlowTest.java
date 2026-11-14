package com.qa.automation.tests.web;

import com.qa.automation.pages.CartPage;
import com.qa.automation.pages.InventoryPage;
import com.qa.automation.tests.BaseTest;
import com.qa.automation.tests.fixtures.TestProducts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartFlowTest extends BaseTest {
    @Test
    @Tag("smoke")
    @Tag("regression")
    void adicionarItemAtualizaContadorEExibeProdutoNoCarrinho() {
        InventoryPage inventory = loginAsStandardUser();
        inventory.addProductToCart(TestProducts.BACKPACK);
        inventory.waitForCartItemCount(1);
        assertEquals(1, inventory.getCartItemCount());

        CartPage cart = inventory.openCart();
        cart.waitForItemCount(1);
        assertEquals(1, cart.getItemCount());
        assertEquals(java.util.List.of(TestProducts.BACKPACK), cart.getItemNames());
    }

    @Test
    @Tag("regression")
    void adicionarDoisItensAtualizaContadorParaDois() {
        InventoryPage inventory = loginAsStandardUser();
        inventory.addProductToCart(TestProducts.BACKPACK);
        inventory.addProductToCart(TestProducts.BIKE_LIGHT);

        inventory.waitForCartItemCount(2);
        assertEquals(2, inventory.getCartItemCount());
    }

    @Test
    @Tag("regression")
    void removerItemNoCatalogoZeraContador() {
        InventoryPage inventory = loginAsStandardUser();
        inventory.addProductToCart(TestProducts.BOLT_T_SHIRT);
        inventory.removeProductFromCart(TestProducts.BOLT_T_SHIRT);

        inventory.waitForCartItemCount(0);
        assertEquals(0, inventory.getCartItemCount());
    }

    @Test
    @Tag("regression")
    void removerItemDentroDoCarrinhoEsvaziaLista() {
        InventoryPage inventory = loginAsStandardUser();
        inventory.addProductToCart(TestProducts.BACKPACK);
        CartPage cart = inventory.openCart();
        cart.waitForItemCount(1);
        assertEquals(1, cart.getItemCount());

        cart.removeProduct(TestProducts.BACKPACK);
        assertTrue(cart.isEmpty());
    }
}
