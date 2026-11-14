package com.qa.automation.tests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.qa.automation.clients.BookingClient;
import com.qa.automation.config.EnvironmentConfig;
import com.qa.automation.models.Booking;
import com.qa.automation.models.CreatedBooking;
import com.qa.automation.tests.fixtures.BookingDataFactory;
import com.qa.automation.tests.fixtures.BookingFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("api")
class BookingApiTest {
    private BookingClient bookingClient;

    @BeforeEach
    void createClient() {
        EnvironmentConfig config = new EnvironmentConfig();
        bookingClient = new BookingClient(config.apiUrl(), config.apiUsername(), config.apiPassword());
    }

    @Test
    @Tag("smoke")
    @Tag("regression")
    void criaReservaEDevolveOsDadosEnviados() {
        Booking payload = BookingDataFactory.buildBooking();
        int bookingId = 0;
        Throwable testFailure = null;
        try {
            BookingClient.ApiResponse response = bookingClient.createBooking(payload);
            assertEquals(200, response.statusCode());
            CreatedBooking created = response.jsonAs(CreatedBooking.class);
            bookingId = created.bookingId();
            assertTrue(bookingId > 0);
            assertEquals(payload, created.booking());
        } catch (RuntimeException | Error failure) {
            testFailure = failure;
            throw failure;
        } finally {
            if (bookingId > 0) {
                try {
                    BookingClient.ApiResponse cleanupResponse = bookingClient.deleteBooking(
                            bookingId, bookingClient.createToken());
                    assertEquals(201, cleanupResponse.statusCode(),
                            "A reserva criada pelo teste deve ser excluída durante a limpeza");
                } catch (RuntimeException | Error cleanupFailure) {
                    if (testFailure != null) {
                        testFailure.addSuppressed(cleanupFailure);
                    } else {
                        throw cleanupFailure;
                    }
                }
            }
        }
    }

    @Test
    @Tag("regression")
    void buscaReservaPorId() {
        try (BookingFixture fixture = BookingFixture.create(bookingClient)) {
            BookingClient.ApiResponse response = bookingClient.getBooking(fixture.id());
            assertEquals(200, response.statusCode());
            assertEquals(fixture.payload(), response.jsonAs(Booking.class));
        }
    }

    @Test
    @Tag("regression")
    void listaReservasFiltrandoPorNome() {
        try (BookingFixture fixture = BookingFixture.create(bookingClient)) {
            BookingClient.ApiResponse response = bookingClient.getBookingIds(fixture.payload().firstname());
            assertEquals(200, response.statusCode());
            JsonNode ids = response.json();
            boolean containsFixture = false;
            for (JsonNode item : ids) {
                if (item.path("bookingid").asInt() == fixture.id()) {
                    containsFixture = true;
                    break;
                }
            }
            assertTrue(containsFixture, "The filtered booking IDs should contain the created booking");
        }
    }

    @Test
    @Tag("regression")
    void atualizaReservaComTokenValido() {
        try (BookingFixture fixture = BookingFixture.create(bookingClient)) {
            Booking updated = BookingDataFactory.withOverrides(fixture.payload(), "Atualizado", 999);
            BookingClient.ApiResponse response = bookingClient.updateBooking(
                    fixture.id(), updated, bookingClient.createToken());
            assertEquals(200, response.statusCode());
            Booking body = response.jsonAs(Booking.class);
            assertEquals("Atualizado", body.firstname());
            assertEquals(999, body.totalprice());
        }
    }

    @Test
    @Tag("smoke")
    @Tag("regression")
    void removeReservaEConfirma404EmSeguida() {
        try (BookingFixture fixture = BookingFixture.create(bookingClient)) {
            BookingClient.ApiResponse deleted = fixture.delete();
            assertEquals(201, deleted.statusCode());
            assertEquals(404, bookingClient.getBooking(fixture.id()).statusCode());
        }
    }

    @Test
    @Tag("regression")
    void atualizarSemTokenRetorna403() {
        try (BookingFixture fixture = BookingFixture.create(bookingClient)) {
            assertEquals(403, bookingClient.updateBooking(
                    fixture.id(), BookingDataFactory.buildBooking(), null).statusCode());
        }
    }

    @Test
    @Tag("regression")
    void removerSemTokenRetorna403() {
        try (BookingFixture fixture = BookingFixture.create(bookingClient)) {
            assertEquals(403, bookingClient.deleteBooking(fixture.id(), null).statusCode());
        }
    }

    @Test
    @Tag("regression")
    void buscarIdInexistenteRetorna404() {
        assertEquals(404, bookingClient.getBooking(999_999_999).statusCode());
    }

    @Test
    @Tag("regression")
    void respostaRespeitaContratoDeTipos() {
        try (BookingFixture fixture = BookingFixture.create(bookingClient)) {
            BookingClient.ApiResponse response = bookingClient.getBooking(fixture.id());
            assertEquals(200, response.statusCode());
            JsonNode body = response.json();
            assertTrue(body.path("firstname").isTextual());
            assertTrue(body.path("lastname").isTextual());
            assertTrue(body.path("totalprice").isNumber());
            assertTrue(body.path("depositpaid").isBoolean());
            assertTrue(body.path("bookingdates").path("checkin").asText().matches("\\d{4}-\\d{2}-\\d{2}"));
            assertTrue(body.path("bookingdates").path("checkout").asText().matches("\\d{4}-\\d{2}-\\d{2}"));
        }
    }

    @Test
    @Tag("smoke")
    @Tag("regression")
    void credenciaisValidasDevolvemToken() {
        BookingClient.ApiResponse response = bookingClient.authenticate();
        assertEquals(200, response.statusCode());
        assertTrue(response.json().path("token").isTextual());
    }

    @Test
    @Tag("regression")
    void credenciaisInvalidasNaoDevolvemToken() {
        BookingClient.ApiResponse response = bookingClient.authenticate("admin", "senha_errada");
        assertEquals(200, response.statusCode());
        JsonNode body = response.json();
        assertFalse(body.has("token"));
        assertEquals("Bad credentials", body.path("reason").asText());
    }
}
