package com.qa.automation.tests.fixtures;

import com.qa.automation.models.Booking;
import com.qa.automation.models.BookingDates;
import java.time.LocalDate;
import java.util.UUID;

public final class BookingDataFactory {
    private BookingDataFactory() { }

    public static Booking buildBooking() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        LocalDate today = LocalDate.now();
        return new Booking(
                "Qa" + suffix,
                "Tester" + suffix,
                150,
                true,
                new BookingDates(today.plusDays(7).toString(), today.plusDays(10).toString()),
                "Breakfast");
    }

    public static Booking withOverrides(Booking original, String firstName, int totalPrice) {
        return new Booking(firstName, original.lastname(), totalPrice, original.depositpaid(),
                original.bookingdates(), original.additionalneeds());
    }
}
