package com.nirvana.application;

import com.nirvana.application.service.impl.BookingEmailContent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Invoice emails print the appointment in the spa's time zone; a bare UTC offset used to crash them. */
class InvoiceEmailTimeTest {
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-22T07:30Z");

    @Test void printsTheSpasLocalTime() {
        assertEquals("Sun 22 Nov 2026, 13:00 IST", BookingEmailContent.formatStart(START, "Asia/Kolkata"));
        assertEquals("Sun 22 Nov 2026, 07:30 GMT", BookingEmailContent.formatStart(START, "Europe/London"));
    }

    @Test void fallsBackToIndiaWithoutAUsableZone() {
        assertEquals("Sun 22 Nov 2026, 13:00 IST", BookingEmailContent.formatStart(START, (String) null));
        assertEquals("Sun 22 Nov 2026, 13:00 IST", BookingEmailContent.formatStart(START, "Not/AZone"));
        assertEquals("N/A", BookingEmailContent.formatStart(null, "Asia/Kolkata"));
    }
}
