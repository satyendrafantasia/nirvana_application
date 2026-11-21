package com.nirvana.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ListingClaimPhoneFormatTest {
    @Test void textsOnlyInternationalNumbers() {
        assertEquals("+918055501234", ListingClaimPhoneService.e164("+91 80 5550 1234"));
        assertEquals("+14155550123", ListingClaimPhoneService.e164("+1 415-555-0123"));
        assertNull(ListingClaimPhoneService.e164("080 5550 1234"));
        assertNull(ListingClaimPhoneService.e164(""));
        assertNull(ListingClaimPhoneService.e164(null));
    }

    @Test void masksAllButCountryCodeAndLastFour() {
        assertEquals("+91 •• •••• 1234", ListingClaimPhoneService.mask("+91 80 5550 1234"));
        assertEquals("+1 •••-•••-0123", ListingClaimPhoneService.mask("+1 415-555-0123"));
    }
}
