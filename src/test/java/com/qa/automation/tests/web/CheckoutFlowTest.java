package com.qa.automation.tests.web;

import com.qa.automation.models.CheckoutInfo;
import com.qa.automation.pages.CartPage;
import com.qa.automation.pages.CheckoutPage;
import com.qa.automation.pages.InventoryPage;
import com.qa.automation.tests.BaseTest;
import com.qa.automation.tests.fixtures.TestProducts;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutFlowTest extends BaseTest {
    private static final CheckoutInfo VALID_INFO = new CheckoutInfo("Maria", "Silva", "72910-000");
    private CheckoutPage checkout;

    @BeforeEach
    void openCheckout() {
        InventoryPage inventory = loginAsStandardUser();
        inventory.addProductToCart(TestProducts.BACKPACK);
        CartPage cart = inventory.openCart();
        checkout = cart.checkout();
        assertTrue(driver.getCurrentUrl().contains("checkout-step-one.html"));
    }

    @Test
    @Tag("smoke")
    @Tag("regression")
    void compraCompletaExibeConfirmacaoDoPedido() {
        checkout.fillInformation(VALID_INFO);
        checkout.continueCheckout();
        assertTrue(driver.getCurrentUrl().contains("checkout-step-two.html"));

        checkout.finish();
        assertEquals("Thank you for your order!", checkout.getCompleteHeader());
    }

    @ParameterizedTest(name = "campo obrigatório ausente: {0}")
    @MethodSource("requiredFieldCases")
    @Tag("regression")
    void campoObrigatorioAusenteExibeErro(String missingField, CheckoutInfo info, String expectedMessage) {
        checkout.fillInformation(info);
        checkout.continueCheckout();
        assertTrue(checkout.getErrorMessage().contains(expectedMessage), missingField);
    }

    private static Stream<Arguments> requiredFieldCases() {
        return Stream.of(
                Arguments.of("firstName", new CheckoutInfo("", VALID_INFO.lastName(), VALID_INFO.postalCode()), "First Name is required"),
                Arguments.of("lastName", new CheckoutInfo(VALID_INFO.firstName(), "", VALID_INFO.postalCode()), "Last Name is required"),
                Arguments.of("postalCode", new CheckoutInfo(VALID_INFO.firstName(), VALID_INFO.lastName(), ""), "Postal Code is required"));
    }
}
