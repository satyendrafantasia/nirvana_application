package com.nirvana.application.service;

/** The configured channel cannot reach this recipient; support can contact them manually. */
public class BookingContactUnavailable extends RuntimeException {
    public BookingContactUnavailable(String message) { super(message); }
}
