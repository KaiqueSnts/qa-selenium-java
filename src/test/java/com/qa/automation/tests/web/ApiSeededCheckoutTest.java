package com.qa.automation.tests.web;

import com.qa.automation.clients.BookingClient;
import com.qa.automation.models.Booking;
import com.qa.automation.models.CheckoutInfo;
import com.qa.automation.pages.CartPage;
import com.qa.automation.pages.CheckoutPage;
import com.qa.automation.pages.InventoryPage;
import com.qa.automation.tests.BaseTest;
import com.qa.automation.tests.fixtures.TestProducts;
import com.qa.automation.tests.fixtures.BookingFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiSeededCheckoutTest extends BaseTest {
    @Test
    @Tag("regression")
    void checkoutUsaDadosDoClienteCriadosViaApi() {
        BookingClient client = new BookingClient(config.apiUrl(), config.apiUsername(), config.apiPassword());
        try (BookingFixture fixture = BookingFixture.create(client)) {
            Booking customer = client.getBooking(fixture.id()).jsonAs(Booking.class);
            assertEquals(fixture.payload().firstname(), customer.firstname());

            InventoryPage inventory = loginAsStandardUser();
            inventory.addProductToCart(TestProducts.BIKE_LIGHT);
            CartPage cart = inventory.openCart();
            CheckoutPage checkout = cart.checkout();
            checkout.fillInformation(new CheckoutInfo(customer.firstname(), customer.lastname(), "72910-000"));
            checkout.continueCheckout();
            checkout.finish();

            assertEquals("Thank you for your order!", checkout.getCompleteHeader());
        }
    }
}
