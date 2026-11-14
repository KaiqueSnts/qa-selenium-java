package com.qa.automation.tests.fixtures;

import com.qa.automation.clients.BookingClient;
import com.qa.automation.models.Booking;
import com.qa.automation.models.CreatedBooking;

/** Creates an API booking for a test and attempts cleanup when closed. */
public final class BookingFixture implements AutoCloseable {
    private final BookingClient client;
    private final String token;
    private final int id;
    private final Booking payload;
    private boolean active = true;

    private BookingFixture(BookingClient client, String token, int id, Booking payload) {
        this.client = client;
        this.token = token;
        this.id = id;
        this.payload = payload;
    }

    public static BookingFixture create(BookingClient client) {
        String token = client.createToken();
        Booking payload = BookingDataFactory.buildBooking();
        BookingClient.ApiResponse response = client.createBooking(payload);
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Could not create booking fixture: " + response.statusCode() + " " + response.body());
        }
        CreatedBooking created = response.jsonAs(CreatedBooking.class);
        return new BookingFixture(client, token, created.bookingId(), payload);
    }

    public int id() { return id; }
    public Booking payload() { return payload; }

    public BookingClient.ApiResponse delete() {
        if (!active) {
            throw new IllegalStateException("Booking " + id + " has already been deleted");
        }

        BookingClient.ApiResponse response = client.deleteBooking(id, token);
        if (response.statusCode() == 201) {
            active = false;
        }
        return response;
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }

        int status = client.deleteBooking(id, token).statusCode();
        if (status != 201) {
            throw new IllegalStateException("Could not clean up booking " + id + ": HTTP " + status);
        }
        active = false;
    }
}
